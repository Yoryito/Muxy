package com.muxy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class SongSourceConverter {
    @TypeConverter
    fun toSource(value: String): SongSource =
        runCatching { SongSource.valueOf(value) }.getOrDefault(SongSource.Imported)

    @TypeConverter
    fun fromSource(source: SongSource): String = source.name
}

/**
 * La 2 solo añade las tablas de listas de reproducción; no toca `songs`.
 *
 * Va como migración de verdad y no como `fallbackToDestructiveMigration` porque
 * a estas alturas ya hay canciones descargadas en el móvil y perder la librería
 * por estrenar las playlists sería un mal negocio.
 *
 * El SQL tiene que coincidir carácter a carácter con lo que genera Room, que lo
 * valida al abrir la base. La referencia es `app/schemas/…/2.json`.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                "`playlistId` INTEGER NOT NULL, " +
                "`songId` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlistId`, `songId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId` " +
                "ON `playlist_songs` (`songId`)",
        )
    }
}

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(SongSourceConverter::class)
abstract class MuxyDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var instance: MuxyDatabase? = null

        fun get(context: Context): MuxyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuxyDatabase::class.java,
                    "muxy.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
