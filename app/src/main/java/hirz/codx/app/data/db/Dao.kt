package hirz.codx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ItemEntity)

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE trashed_at IS NULL ORDER BY imported_at DESC")
    fun observeActive(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE trashed_at IS NOT NULL ORDER BY trashed_at DESC")
    fun observeTrash(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE sha256_hex = :sha AND trashed_at IS NULL LIMIT 1")
    suspend fun findActiveBySha256(sha: String): ItemEntity?

    @Query("SELECT COUNT(*) FROM items WHERE trashed_at IS NULL")
    suspend fun countActive(): Long

    @Query("UPDATE items SET trashed_at = :trashedAt WHERE id IN (:ids)")
    suspend fun markTrashed(ids: List<String>, trashedAt: Long?)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT * FROM items WHERE trashed_at IS NOT NULL AND trashed_at < :cutoff")
    suspend fun trashedBefore(cutoff: Long): List<ItemEntity>
}

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity)

    @Query("SELECT * FROM albums ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(link: AlbumItemEntity)

    @Query("DELETE FROM album_items WHERE album_id = :albumId AND item_id = :itemId")
    suspend fun removeItem(albumId: String, itemId: String)
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToItem(link: ItemTagEntity)

    @Query("DELETE FROM item_tags WHERE item_id = :itemId AND tag_id = :tagId")
    suspend fun removeFromItem(itemId: String, tagId: String)
}

@Dao
interface SubtitleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subtitle: SubtitleEntity)

    @Query("SELECT * FROM subtitles WHERE item_id = :itemId ORDER BY created_at ASC")
    suspend fun forItem(itemId: String): List<SubtitleEntity>

    @Query("DELETE FROM subtitles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?
}
