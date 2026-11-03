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
    entities = [Item::class, Acquisition::class, Photo::class, Line::class, LineStation::class],
    version = CollectiblesDb.VERSION,
    exportSchema = true,
)
abstract class CollectiblesDb : RoomDatabase() {
    abstract fun dao(): CollectionDao

    companion object {
        const val NAME = "collectibles.db"
        const val VERSION = 1

        /** Used by the app and by restore, so an older backup migrates the same way the app does. */
        val MIGRATIONS = emptyArray<Migration>()
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
