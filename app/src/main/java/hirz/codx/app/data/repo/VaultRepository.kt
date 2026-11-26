package hirz.codx.app.data.repo

import android.content.Context
import hirz.codx.app.crypto.Aead
import hirz.codx.app.crypto.Bytes
import hirz.codx.app.crypto.FileKeys
import hirz.codx.app.crypto.HirzVault
import hirz.codx.app.data.db.HirzDatabase
import hirz.codx.app.data.db.ItemEntity
import hirz.codx.app.data.db.MetadataCipher
import hirz.codx.app.data.model.ImportState
import hirz.codx.app.data.model.ItemType
import hirz.codx.app.data.store.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val vault: HirzVault = HirzVault.get(context),
    private val db: HirzDatabase = HirzDatabase.get(context)
) {
    private val store = EncryptedFileStore(File(context.filesDir, "vault"))
    private val random = SecureRandom()

    fun observeActiveItems(): Flow<List<ItemEntity>> = db.items().observeActive()

    suspend fun importStream(
        input: InputStream,
        originalName: String,
        originalPath: String,
        mimeType: String,
        type: ItemType,
        sizeBytes: Long,
        capturedAt: Long?,
        modifiedAt: Long?
    ): ItemEntity = withContext(Dispatchers.IO) {
        val mk = vault.currentMasterKey() ?: error("vault locked")
        val meta = MetadataCipher.fromMasterKey(mk)
        val itemId = UUID.randomUUID().toString()
        val rawFileKey = ByteArray(32).also { random.nextBytes(it) }
        val aead = FileKeys.streamingAead(rawFileKey)
        val aad = FileKeys.aadForItem(itemId)

        val write = store.write(input, aead, aad)
        val wrapped = Aead.seal(mk, rawFileKey, "hirz.filekey.v1:$itemId")
        val name = meta.encryptString(originalName, "hirz.name.v1:$itemId")
        val path = meta.encryptString(originalPath, "hirz.path.v1:$itemId")
        val mime = meta.encryptString(mimeType, "hirz.mime.v1:$itemId")

        val entity = ItemEntity(
            id = itemId,
            type = type.name,
            randomName = write.name,
            wrappedFileKeyNonce = wrapped.nonce,
            wrappedFileKeyCiphertext = wrapped.ciphertext,
            nameNonce = name.nonce,
            nameCiphertext = name.ciphertext,
            pathNonce = path.nonce,
            pathCiphertext = path.ciphertext,
            mimeNonce = mime.nonce,
            mimeCiphertext = mime.ciphertext,
            sizeBytes = sizeBytes,
            sha256Hex = write.sha256Hex,
            capturedAt = capturedAt,
            modifiedAt = modifiedAt,
            importedAt = System.currentTimeMillis(),
            importState = ImportState.VERIFIED.name,
            trashedAt = null,
            favorite = false,
            playbackPositionMs = 0L
        )
        db.items().insert(entity)
        Bytes.wipe(rawFileKey, mk)
        entity
    }

    suspend fun decryptToTemp(item: ItemEntity): File = withContext(Dispatchers.IO) {
        val mk = vault.currentMasterKey() ?: error("vault locked")
        val rawFileKey = Aead.open(
            mk,
            Aead.Blob(item.wrappedFileKeyNonce, item.wrappedFileKeyCiphertext),
            "hirz.filekey.v1:${item.id}"
        ) ?: error("file key unwrap failed")
        val out = File(context.cacheDir, "dec-${item.id}-${System.currentTimeMillis()}")
        out.outputStream().use { sink ->
            store.readAll(item.randomName, FileKeys.streamingAead(rawFileKey), FileKeys.aadForItem(item.id), sink)
        }
        Bytes.wipe(rawFileKey, mk)
        out
    }
}
