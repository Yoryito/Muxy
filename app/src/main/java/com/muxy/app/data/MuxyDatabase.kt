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

/**
 * La 3 añade [Song.gainDb], para la normalización de volumen entre canciones.
 *
 * Una columna sola con `ADD COLUMN` es mucho menos arriesgada que la migración
 * de tablas de la 2: no reescribe nada, solo añade un campo con un valor por
 * defecto que las filas ya existentes heredan tal cual (0 = sin ajuste, que es
 * exactamente el volumen que tenían hasta ahora).
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN gainDb REAL NOT NULL DEFAULT 0.0")
    }
}

/**
 * La 4 añade [Song.lastPlayedAt], para la sección de "recientes" del inicio.
 *
 * Igual que la 3: una columna sola con `ADD COLUMN`, sin reescribir nada. Es
 * `NULL` por defecto porque no hay forma de saber cuándo sonó por última vez
 * una canción que ya estaba en la librería antes de este cambio — no aparecer
 * en "recientes" es lo correcto para esas.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL")
    }
}

/**
 * La 5 añade el contador de reproducciones de cada canción y la fecha de última
 * escucha de cada lista, que es lo que permite el "Mi Top" y que los recientes
 * del inicio mezclen canciones y playlists.
 *
 * Las dos son columnas sueltas otra vez. El contador arranca en 0 para todo lo
 * que ya estaba: no hay historial anterior del que deducirlo, y estrenar la
 * sección con el ranking vacío es más honesto que inventarse un orden.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN lastPlayedAt INTEGER DEFAULT NULL")
    }
}

@Database(
    entities = [Song::class, Playlist::class, PlaylistSong::class],
    version = 5,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
