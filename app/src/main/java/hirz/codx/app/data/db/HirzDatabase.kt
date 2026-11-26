package hirz.codx.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        AlbumEntity::class,
        AlbumItemEntity::class,
        TagEntity::class,
        ItemTagEntity::class,
        SubtitleEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class HirzDatabase : RoomDatabase() {
    abstract fun items(): ItemDao
    abstract fun albums(): AlbumDao
    abstract fun tags(): TagDao
    abstract fun subtitles(): SubtitleDao
    abstract fun settings(): SettingDao

    companion object {
        @Volatile private var instance: HirzDatabase? = null

        fun get(context: Context): HirzDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HirzDatabase::class.java,
                "hirz.db"
            )
                .openHelperFactory(null) // plaintext DB file but sensitive columns are encrypted
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
