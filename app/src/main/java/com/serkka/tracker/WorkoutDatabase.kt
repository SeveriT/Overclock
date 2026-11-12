package com.serkka.tracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Workout::class, BodyWeight::class, Note::class, WorkoutSession::class, ScheduleItem::class], version = 10, exportSchema = false)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun bodyWeightDao(): BodyWeightDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun scheduleItemDao(): ScheduleItemDao

    companion object {
        @Volatile
        private var Instance: WorkoutDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `body_weights` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `date` INTEGER NOT NULL, 
                        `weight` REAL NOT NULL, 
                        `notes` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `date` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `schedule_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `isDone` INTEGER NOT NULL DEFAULT 0,
                        `position` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        // Optional RPE per workout entry. Nullable REAL with no default = existing rows read as
        // "not recorded" (matches Workout.rpe: Float? = null).
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workouts ADD COLUMN rpe REAL")
            }
        }

        // Bluetooth heart rate per timed session: average, max and the sampled graph.
        // Nullable columns without defaults = existing sessions read as "not recorded".
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN avgHr INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN maxHr INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN hrSamples TEXT")
            }
        }

        fun getDatabase(context: Context): WorkoutDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, WorkoutDatabase::class.java, "workout_db")
                    // Every version step needs a migration here: the destructive fallback
                    // below would otherwise wipe all data on upgrade.
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }

        fun resetInstance() {
            Instance = null
        }
    }
}
