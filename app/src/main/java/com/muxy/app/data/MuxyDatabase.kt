package com.muxy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class SongSourceConverter {
    @TypeConverter
    fun toSource(value: String): SongSource =
        runCatching { SongSource.valueOf(value) }.getOrDefault(SongSource.Imported)

    @TypeConverter
    fun fromSource(source: SongSource): String = source.name
}

@Database(entities = [Song::class], version = 1, exportSchema = true)
@TypeConverters(SongSourceConverter::class)
abstract class MuxyDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var instance: MuxyDatabase? = null

        fun get(context: Context): MuxyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuxyDatabase::class.java,
                    "muxy.db",
                ).build().also { instance = it }
            }
    }
}
