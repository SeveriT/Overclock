package com.serkka.tracker

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ── Session export (TCX) ─────────────────────────────────────────────────────
// Writes a session as a Training Center XML (.tcx) file: start time, duration and the heart
// rate graph (one trackpoint per HeartRateMonitor.SAMPLE_SECONDS). Strava (strava.com/upload),
// Garmin Connect, intervals.icu and most training sites accept TCX. No GPS: it's a gym workout.
// The file is shared through Android's share sheet (Drive, email, ...) or saved to a folder the
// user picks (Android's file picker, via the Sessions export menu).

object SessionExport {
    const val MIME_TCX = "application/vnd.garmin.tcx+xml"

    /** Builds the TCX document for [session]. Sessions without heart rate export time only. */
    fun buildTcx(session: WorkoutSession): String {
        val start = Instant.ofEpochMilli(session.date).truncatedTo(ChronoUnit.SECONDS)
        fun time(offsetSeconds: Long) = start.plusSeconds(offsetSeconds).toString()   // ISO-8601 UTC
        val samples = session.hrSampleList()

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""").append('\n')
        sb.append("  <Activities>\n")
        sb.append("""    <Activity Sport="Other">""").append('\n')   // TCX only knows Running / Biking / Other
        sb.append("      <Id>${time(0)}</Id>\n")
        sb.append("""      <Lap StartTime="${time(0)}">""").append('\n')
        sb.append("        <TotalTimeSeconds>${session.durationSeconds}</TotalTimeSeconds>\n")
        sb.append("        <DistanceMeters>0</DistanceMeters>\n")
        sb.append("        <Calories>0</Calories>\n")
        session.avgHr?.let { sb.append("        <AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>\n") }
        session.maxHr?.let { sb.append("        <MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>\n") }
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("        <Track>\n")
        // Start and end points anchor the full duration even where the strap had no reading
        sb.append("          <Trackpoint><Time>${time(0)}</Time></Trackpoint>\n")
        samples.forEachIndexed { i, bpm ->
            if (bpm <= 0) return@forEachIndexed          // gap: no reading at that moment
            val offset = (i + 1L) * HeartRateMonitor.SAMPLE_SECONDS
            if (offset >= session.durationSeconds) return@forEachIndexed
            sb.append("          <Trackpoint><Time>${time(offset)}</Time>")
            sb.append("<HeartRateBpm><Value>$bpm</Value></HeartRateBpm></Trackpoint>\n")
        }
        sb.append("          <Trackpoint><Time>${time(session.durationSeconds.toLong())}</Time></Trackpoint>\n")
        sb.append("        </Track>\n")
        sb.append("      </Lap>\n")
        val notes = listOf(session.name, session.notes).filter { it.isNotBlank() }.joinToString("\n")
        if (notes.isNotEmpty()) sb.append("      <Notes>${escapeXml(notes)}</Notes>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }

    /** Writes [session] to a .tcx file and opens the share sheet for it. */
    fun share(context: Context, session: WorkoutSession) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }      // only the latest export is kept
            val file = File(dir, fileName(session))
            file.writeText(buildTcx(session))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = MIME_TCX
                putExtra(Intent.EXTRA_STREAM, uri)
                // Drive (and some file savers) name the saved file after the subject/title, so
                // both carry the full file name: without ".tcx" Strava refuses the upload.
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                putExtra(Intent.EXTRA_TITLE, file.name)
                clipData = android.content.ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, "Export \"${session.name}\"").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e("SessionExport", "Export failed", e)
            Toast.makeText(context, "Couldn't export the session", Toast.LENGTH_SHORT).show()
        }
    }

    /** Writes [session] as TCX into [uri], a file the user created with the system file picker. */
    fun save(context: Context, uri: android.net.Uri, session: WorkoutSession) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")!!.use {
                it.write(buildTcx(session).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(context, "Session saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SessionExport", "Save failed", e)
            Toast.makeText(context, "Couldn't save the session", Toast.LENGTH_SHORT).show()
        }
    }

    /** "2026-11-12 Leg Day.tcx" with characters file systems dislike removed. */
    fun fileName(session: WorkoutSession): String {
        val date = Instant.ofEpochMilli(session.date).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val name = session.name.replace(Regex("""[\\/:*?"<>|]"""), "").replace('–', '-').trim().take(60)
        return "$date $name.tcx"
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
