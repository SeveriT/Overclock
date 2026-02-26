package com.serkka.tracker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class BackupManager(private val context: Context) {

    private val dbName = "workout_db"

    suspend fun backupDatabase(destinationUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = WorkoutDatabase.getDatabase(context)
            // Force a Checkpoint
            db.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { cursor ->
                if (cursor.moveToFirst()) {
                    Log.d("BackupManager", "Checkpoint result: ${cursor.getInt(0)}")
                }
            }

            val dbFile = context.getDatabasePath(dbName)
            if (dbFile.exists()) {
                copyFile(dbFile, destinationUri)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Backup failed", e)
            false
        }
    }

    suspend fun restoreDatabase(uris: List<Uri>): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Close and Reset
            WorkoutDatabase.getDatabase(context).close()
            WorkoutDatabase.resetInstance()

            // 2. Clean up current files
            val dbPath = context.getDatabasePath(dbName)
            val walFile = File(dbPath.path + "-wal")
            val shmFile = File(dbPath.path + "-shm")
            
            if (dbPath.exists()) dbPath.delete()
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            // 3. Restore selected files
            for (uri in uris) {
                val fileName = getFileName(uri) ?: continue
                val targetFile = when {
                    fileName.endsWith("-wal") -> walFile
                    fileName.endsWith("-shm") -> shmFile
                    else -> dbPath // Default to main db file
                }
                copyFileFromUri(uri, targetFile)
                Log.d("BackupManager", "Restored $fileName to ${targetFile.name} (Size: ${targetFile.length()})")
            }
            
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Restore failed", e)
            false
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        }
        return result ?: uri.path?.let { path ->
            val cut = path.lastIndexOf('/')
            if (cut != -1) path.substring(cut + 1) else path
        }
    }

    private fun copyFile(sourceFile: File, destUri: Uri) {
        context.contentResolver.openOutputStream(destUri)?.use { output ->
            FileInputStream(sourceFile).use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun copyFileFromUri(sourceUri: Uri, destFile: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
