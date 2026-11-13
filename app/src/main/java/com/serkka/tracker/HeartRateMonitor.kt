package com.serkka.tracker

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.serkka.tracker.HeartRateMonitor.SCAN_TIMEOUT_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// ── Bluetooth heart rate (standard BLE Heart Rate Service) ───────────────────
// Works with any strap or watch that broadcasts the standard Heart Rate Service (0x180D):
// Polar H9/H10, Garmin HRM, Wahoo TICKR, Coospo, most "HR broadcast" watch modes, etc.
// The last connected strap is remembered and reconnected automatically when the timer opens.
// While a timed workout runs, the current BPM is sampled every SAMPLE_SECONDS into a list
// that is saved with the session (WorkoutSession.hrSamples).

object HeartRateMonitor {
    private const val TAG = "HeartRateMonitor"
    private val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** One saved BPM value per this many seconds of active (unpaused) workout time. */
    const val SAMPLE_SECONDS = 5

    private const val SCAN_TIMEOUT_MS = 15_000L
    private const val STALE_READING_MS = 5_000L      // no notification for this long = no reading
    private const val PREF_ADDRESS = "hr_device_address"
    private const val PREF_NAME = "hr_device_name"

    sealed interface State {
        data object Idle : State                              // nothing connected
        data class Connecting(val name: String) : State
        data class Connected(val name: String) : State
        data class Reconnecting(val name: String) : State     // dropped; waiting for it to come back
    }

    data class Device(val name: String, val address: String, val rssi: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** Latest heart rate, or null when there's no fresh reading. */
    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices

    private var gatt: BluetoothGatt? = null
    private var userDisconnected = false
    private var scanTimeoutJob: Job? = null
    private var staleJob: Job? = null
    private var reconnectJob: Job? = null
    private var appContext: Context? = null

    // ── Permissions / adapter ───────────────────────────────────────────────

    /** Runtime permissions BLE needs: scan+connect on Android 12+, location before that. */
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context): Boolean = requiredPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun adapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun isSupported(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && adapter(context) != null

    fun isBluetoothOn(context: Context): Boolean = adapter(context)?.isEnabled == true

    fun savedDeviceName(context: Context): String? = prefs(context).getString(PREF_NAME, null)

    private fun prefs(context: Context) = PreferencesManager.getInstance(context).tracker

    // ── Scanning ────────────────────────────────────────────────────────────

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: "Heart rate monitor"
            val device = Device(name, result.device.address, result.rssi)
            // Keep one entry per strap, strongest signal first
            _devices.value = (_devices.value.filterNot { it.address == device.address } + device)
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _scanning.value = false
        }
    }

    /** Looks for nearby heart rate straps for [SCAN_TIMEOUT_MS]; results land in [devices]. */
    @SuppressLint("MissingPermission")
    fun startScan(context: Context) {
        if (!hasPermissions(context) || !isBluetoothOn(context)) return
        val scanner = adapter(context)?.bluetoothLeScanner ?: return
        stopScan(context)
        _devices.value = emptyList()
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }
            .onSuccess {
                _scanning.value = true
                scanTimeoutJob = scope.launch { delay(SCAN_TIMEOUT_MS); stopScan(context) }
            }
            .onFailure { Log.e(TAG, "startScan failed", it) }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(context: Context) {
        scanTimeoutJob?.cancel()
        if (!_scanning.value) return
        _scanning.value = false
        if (hasPermissions(context)) {
            runCatching { adapter(context)?.bluetoothLeScanner?.stopScan(scanCallback) }
        }
    }

    // ── Connection ──────────────────────────────────────────────────────────

    /** Connects to [device] and remembers it for automatic reconnects. */
    fun connect(context: Context, device: Device) {
        prefs(context).edit()
            .putString(PREF_ADDRESS, device.address)
            .putString(PREF_NAME, device.name)
            .apply()
        connectAddress(context, device.address, device.name, autoConnect = false)
    }

    /** Reconnects to the remembered strap if nothing is connected yet (called when the timer opens). */
    fun reconnectSaved(context: Context) {
        if (_state.value != State.Idle) return
        if (!hasPermissions(context) || !isBluetoothOn(context)) return
        val p = prefs(context)
        val address = p.getString(PREF_ADDRESS, null) ?: return
        val name = p.getString(PREF_NAME, null) ?: "Heart rate monitor"
        // autoConnect = true: Android waits for the strap to come into range, no timeout
        connectAddress(context, address, name, autoConnect = true)
    }

    @SuppressLint("MissingPermission")
    private fun connectAddress(context: Context, address: String, name: String, autoConnect: Boolean) {
        if (!hasPermissions(context)) return
        val adapter = adapter(context) ?: return
        appContext = context.applicationContext
        stopScan(context)
        reconnectJob?.cancel()
        closeGatt()
        userDisconnected = false
        _state.value = if (autoConnect) State.Reconnecting(name) else State.Connecting(name)
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: run {
            _state.value = State.Idle
            return
        }
        gatt = device.connectGatt(context.applicationContext, autoConnect, GattCallback(name), BluetoothDevice.TRANSPORT_LE)
    }

    /** Disconnects; with [forget] the strap is no longer reconnected automatically. */
    fun disconnect(context: Context, forget: Boolean = false) {
        userDisconnected = true
        reconnectJob?.cancel()
        closeGatt()
        _state.value = State.Idle
        setBpm(null)
        if (forget) prefs(context).edit().remove(PREF_ADDRESS).remove(PREF_NAME).apply()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val g = gatt ?: return
        gatt = null
        runCatching { g.disconnect() }
        runCatching { g.close() }
    }

    private class GattCallback(private val name: String) : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                onLinkLost(g, name)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                Log.e(TAG, "No heart rate service on $name (status $status)")
                onLinkLost(g, name)
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD)
            if (descriptor != null) {
                val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, enable)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = enable
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            _state.value = State.Connected(name)
        }

        // Android 13+
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == HR_MEASUREMENT) parseBpm(value)?.let { setBpm(it) }
        }

        // Android 12 and older
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return   // handled above
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (characteristic.uuid == HR_MEASUREMENT) parseBpm(value)?.let { setBpm(it) }
        }
    }

    /** Strap dropped (out of range, battery, strap off): keep trying unless the user disconnected. */
    @SuppressLint("MissingPermission")
    private fun onLinkLost(g: BluetoothGatt, name: String) {
        if (g != gatt) { runCatching { g.close() }; return }   // a stale connection we already replaced
        closeGatt()
        setBpm(null)
        if (userDisconnected) { _state.value = State.Idle; return }
        _state.value = State.Reconnecting(name)
        val ctx = appContext ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2_000)
            val address = prefs(ctx).getString(PREF_ADDRESS, null)
            if (address == null || userDisconnected) { _state.value = State.Idle; return@launch }
            connectAddress(ctx, address, name, autoConnect = true)
        }
    }

    /**
     * Heart Rate Measurement (0x2A37): flags byte, bit 0 = value is UINT16 instead of UINT8.
     * 0 means "no contact" on many straps, so it reads as no value.
     */
    private fun parseBpm(value: ByteArray): Int? {
        if (value.size < 2) return null
        val flags = value[0].toInt()
        val bpm = if (flags and 0x01 == 0) value[1].toInt() and 0xFF
        else if (value.size >= 3) (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        else return null
        return bpm.takeIf { it in 25..250 }
    }

    private fun setBpm(value: Int?) {
        _bpm.value = value
        staleJob?.cancel()
        if (value != null) {
            staleJob = scope.launch { delay(STALE_READING_MS); _bpm.value = null }
        }
    }

    // ── Recording (driven by the workout timer) ─────────────────────────────

    private val samples = mutableListOf<Int>()
    private var recordJob: Job? = null

    /** The running workout's samples so far (0 = no reading), for the live graph in the timer ring. */
    private val _recorded = MutableStateFlow<List<Int>>(emptyList())
    val recorded: StateFlow<List<Int>> = _recorded

    /** Call inside synchronized(samples) after every change. */
    private fun publishSamples() {
        _recorded.value = samples.toList()
    }

    /** New workout: clears old samples and starts sampling. */
    fun startRecording() {
        synchronized(samples) { samples.clear(); publishSamples() }
        resumeRecording()
    }

    /**
     * Continues sampling after a pause. [padToSeconds] fills the gap with "no reading" when the
     * app was restarted mid-workout, so the graph still lines up with the elapsed time.
     */
    fun resumeRecording(padToSeconds: Long = 0L) {
        if (recordJob?.isActive == true) return
        synchronized(samples) {
            val missing = (padToSeconds / SAMPLE_SECONDS).toInt() - samples.size
            repeat(missing.coerceAtLeast(0)) { samples.add(0) }
            publishSamples()
        }
        recordJob = scope.launch {
            while (true) {
                delay(SAMPLE_SECONDS * 1000L)
                synchronized(samples) { samples.add(_bpm.value ?: 0); publishSamples() }
            }
        }
    }

    fun pauseRecording() {
        recordJob?.cancel()
        recordJob = null
    }

    /** Ends the workout's recording and returns its samples (empty if no strap was ever read). */
    fun finishRecording(): List<Int> {
        pauseRecording()
        val out = synchronized(samples) { samples.toList().also { samples.clear(); publishSamples() } }
        return if (out.any { it > 0 }) out else emptyList()
    }

    fun discardRecording() {
        pauseRecording()
        synchronized(samples) { samples.clear(); publishSamples() }
    }
}
