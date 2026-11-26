package hirz.codx.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "random_name") val randomName: String,
    @ColumnInfo(name = "wrapped_file_key_nonce") val wrappedFileKeyNonce: ByteArray,
    @ColumnInfo(name = "wrapped_file_key_ct") val wrappedFileKeyCiphertext: ByteArray,
    @ColumnInfo(name = "name_nonce") val nameNonce: ByteArray,
    @ColumnInfo(name = "name_ct") val nameCiphertext: ByteArray,
    @ColumnInfo(name = "path_nonce") val pathNonce: ByteArray,
    @ColumnInfo(name = "path_ct") val pathCiphertext: ByteArray,
    @ColumnInfo(name = "mime_nonce") val mimeNonce: ByteArray,
    @ColumnInfo(name = "mime_ct") val mimeCiphertext: ByteArray,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "sha256_hex") val sha256Hex: String,
    @ColumnInfo(name = "captured_at") val capturedAt: Long?,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long?,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "import_state") val importState: String,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long?,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    @ColumnInfo(name = "playback_position_ms") val playbackPositionMs: Long
)

@Entity(
    tableName = "albums",
    indices = [Index(value = ["created_at"])]
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_nonce") val nameNonce: ByteArray,
    @ColumnInfo(name = "name_ct") val nameCiphertext: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "album_items",
    primaryKeys = ["album_id", "item_id"],
    indices = [Index(value = ["item_id"])]
)
data class AlbumItemEntity(
    @ColumnInfo(name = "album_id") val albumId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_nonce") val nameNonce: ByteArray,
    @ColumnInfo(name = "name_ct") val nameCiphertext: ByteArray,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "item_tags",
    primaryKeys = ["item_id", "tag_id"],
    indices = [Index(value = ["tag_id"])]
)
data class ItemTagEntity(
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "tag_id") val tagId: String
)

@Entity(
    tableName = "subtitles",
    indices = [Index(value = ["item_id"])]
)
data class SubtitleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "random_name") val randomName: String,
    @ColumnInfo(name = "wrapped_file_key_nonce") val wrappedFileKeyNonce: ByteArray,
    @ColumnInfo(name = "wrapped_file_key_ct") val wrappedFileKeyCiphertext: ByteArray,
    @ColumnInfo(name = "label_nonce") val labelNonce: ByteArray,
    @ColumnInfo(name = "label_ct") val labelCiphertext: ByteArray,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String
)
