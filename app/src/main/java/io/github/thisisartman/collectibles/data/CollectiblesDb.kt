package io.github.thisisartman.collectibles.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Schema changes always ship with a migration and a migration test; there is no destructive
 * fallback, because this database holds real finds (BUILD_PLAN ground rules).
 */
@Database(
    entities = [Item::class, Acquisition::class, Photo::class, Line::class, LineStation::class, CollectionType::class],
    version = CollectiblesDb.VERSION,
    exportSchema = true,
)
abstract class CollectiblesDb : RoomDatabase() {
    abstract fun dao(): CollectionDao

    companion object {
        const val NAME = "collectibles.db"
        const val VERSION = 5

        /**
         * v1 to v2 (2026-11-07), additions only, so every v1 row and every v1 backup stays as it is:
         * custom collections (a new table, and a nullable column on item) and a quantity per find (default 1).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_type` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `name_ja` TEXT, " +
                        "`color` INTEGER NOT NULL, `icon` TEXT NOT NULL, `has_cost` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE `item` ADD COLUMN `custom_type_id` TEXT")
                db.execSQL("ALTER TABLE `acquisition` ADD COLUMN `quantity` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** v2 to v3 (2026-11-07), two nullable columns: when you found nothing at an item's place, and its chosen photo. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `item` ADD COLUMN `none_here_at` INTEGER")
                db.execSQL("ALTER TABLE `item` ADD COLUMN `cover_photo_id` TEXT")
            }
        }

        /** v3 to v4 (2026-11-08): when you edited an item's details, so catalogue updates keep your edits. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `item` ADD COLUMN `edited_at` INTEGER")
            }
        }

        /**
         * v4 to v5 (2026-11-26, #194): no more "had before Meguri". Every undated find gets the day it was added (its
         * created_at; the moment of the migration if that is missing), and its before_app flag is cleared. Rows only,
         * no schema change: the column stays, so older backups still read, and they come through here too.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE `acquisition` SET `acquired_at` = COALESCE(NULLIF(`created_at`, 0), ?), `before_app` = 0 " +
                        "WHERE `before_app` = 1 OR `acquired_at` IS NULL",
                    arrayOf<Any>(System.currentTimeMillis()),
                )
            }
        }

        /** Used by the app and by restore, so an older backup migrates the same way the app does. */
        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): CollectiblesDb =
        Room.databaseBuilder(context, CollectiblesDb::class.java, CollectiblesDb.NAME).addMigrations(*CollectiblesDb.MIGRATIONS).build()

    @Provides
    fun dao(db: CollectiblesDb): CollectionDao = db.dao()
}
