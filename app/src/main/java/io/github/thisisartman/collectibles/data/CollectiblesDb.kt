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
        const val VERSION = 2

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

        /** Used by the app and by restore, so an older backup migrates the same way the app does. */
        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
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
