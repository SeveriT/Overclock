package com.serkka.tracker

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(private val context: Context) {

    private val dbName = "workout_db"
    private val stepPrefsName = "step_counter"

    companion object {
        private const val ENTRY_DB = "workout_db"
        private const val ENTRY_STEPS = "step_counter.json"
        // ZIP magic number: "PK\x03\x04"
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }

    /** Builds a zip bundle containing the checkpointed DB and step-counter prefs. */
    suspend fun buildBackupFile(): File? = withContext(Dispatchers.IO) {
        val db = WorkoutDatabase.getDatabase(context)
        db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
            if (cursor.moveToFirst()) Log.d("BackupManager", "Checkpoint: ${cursor.getInt(0)}")
        }
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return@withContext null

        val zipFile = File(context.cacheDir, "tracker_backup.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry(ENTRY_DB))
            FileInputStream(dbFile).use { it.copyTo(zos) }
            zos.closeEntry()

            zos.putNextEntry(ZipEntry(ENTRY_STEPS))
            zos.write(serializePrefs(context.getSharedPreferences(stepPrefsName, Context.MODE_PRIVATE)).toByteArray())
            zos.closeEntry()
        }
        zipFile
    }

    suspend fun backupDatabase(destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val zip = buildBackupFile() ?: return@withContext false
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                FileInputStream(zip).use { it.copyTo(out) }
            }
            zip.delete()
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Backup failed", e)
            false
        }
    }

    suspend fun restoreDatabase(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        val dbPath  = context.getDatabasePath(dbName)
        val walFile = File(dbPath.path + "-wal")
        val shmFile = File(dbPath.path + "-shm")

        val tempDb  = File(dbPath.path + ".restore_tmp")
        val tempWal = File(dbPath.path + "-wal.restore_tmp")
        val tempShm = File(dbPath.path + "-shm.restore_tmp")

        return@withContext try {
            var restoredStepsJson: String? = null

            if (uris.size == 1 && isZip(uris[0])) {
                // New zip bundle path
                context.contentResolver.openInputStream(uris[0])?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            when (entry.name) {
                                ENTRY_DB -> FileOutputStream(tempDb).use { zis.copyTo(it) }
                                ENTRY_STEPS -> restoredStepsJson = zis.readBytes().toString(Charsets.UTF_8)
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                // Legacy path: individual db / db-wal / db-shm files
                for (uri in uris) {
                    val fileName = getFileName(uri) ?: continue
                    val tempTarget = when {
                        fileName.endsWith("-wal") -> tempWal
                        fileName.endsWith("-shm") -> tempShm
                        else                      -> tempDb
                    }
                    copyFileFromUri(uri, tempTarget)
                    Log.d("BackupManager", "Staged $fileName → ${tempTarget.name} (${tempTarget.length()} bytes)")
                }
            }

            if (!tempDb.exists()) {
                Log.e("BackupManager", "No main db file found in backup")
                return@withContext false
            }

            WorkoutDatabase.getDatabase(context).close()
            WorkoutDatabase.resetInstance()

            if (dbPath.exists())  dbPath.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            tempDb.renameTo(dbPath)
            if (tempWal.exists()) tempWal.renameTo(walFile)
            if (tempShm.exists()) tempShm.renameTo(shmFile)

            restoredStepsJson?.let {
                applyPrefsJson(context.getSharedPreferences(stepPrefsName, Context.MODE_PRIVATE), it)
            }

            Log.d("BackupManager", "Restore complete")
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Restore failed", e)
            tempDb.delete(); tempWal.delete(); tempShm.delete()
            false
        }
    }

    private fun isZip(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                read == 4 && header.contentEquals(ZIP_MAGIC)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun serializePrefs(prefs: SharedPreferences): String {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            val entry = JSONObject()
            when (value) {
                is Boolean -> { entry.put("type", "bool"); entry.put("value", value) }
                is Int     -> { entry.put("type", "int"); entry.put("value", value) }
                is Long    -> { entry.put("type", "long"); entry.put("value", value) }
                is Float   -> { entry.put("type", "float"); entry.put("value", value.toDouble()) }
                is String  -> { entry.put("type", "string"); entry.put("value", value) }
                else       -> continue
            }
            json.put(key, entry)
        }
        return json.toString()
    }

    private fun applyPrefsJson(prefs: SharedPreferences, jsonStr: String) {
        val json = JSONObject(jsonStr)
        val editor = prefs.edit()
        editor.clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = json.getJSONObject(key)
            when (entry.getString("type")) {
                "bool"   -> editor.putBoolean(key, entry.getBoolean("value"))
                "int"    -> editor.putInt(key, entry.getInt("value"))
                "long"   -> editor.putLong(key, entry.getLong("value"))
                "float"  -> editor.putFloat(key, entry.getDouble("value").toFloat())
                "string" -> editor.putString(key, entry.getString("value"))
            }
        }
        editor.apply()
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) result = cursor.getString(index)
                    }
                } finally {
                    cursor.close()
                }
            }
        }
        return result ?: uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }

    private fun copyFileFromUri(sourceUri: Uri, destFile: File) {
        val input = context.contentResolver.openInputStream(sourceUri)
        if (input != null) {
            try {
                val output = FileOutputStream(destFile)
                try {
                    input.copyTo(output)
                } finally {
                    output.close()
                }
            } finally {
                input.close()
            }
        }
    }
}
