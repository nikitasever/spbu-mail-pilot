package ru.local.spbumailpilot

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Encrypted account collection; version-1 single records migrate on the next atomic write. */
internal class NativeDraftStore(context: Context, private val owner: String) {
    private val file: AtomicFile
    init {
        require(owner.matches(Regex("account-[0-9a-f]{64}")))
        val directory = File(context.filesDir, "native-drafts").apply { mkdirs() }
        file = AtomicFile(File(directory, "$owner.bin"))
    }
    private fun book(): NativeDraftBook {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return NativeDraftBook.empty(owner)
        require(OfflineMailStore.committedSnapshot(file.baseFile).length() <= MAX_BYTES)
        val packet = file.readFully()
        require(packet.size <= MAX_BYTES)
        val plain = OfflineMailStore.crypt(false, key(false), "native-draft/v1/$owner", packet)
        return try { NativeDraftBook.parse(owner, plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
    }
    fun read(): NativeDraft? = book().active()
    fun list(): List<NativeDraft> = book().list()
    fun find(id:String): NativeDraft? = book().find(id)
    fun create(record:NativeDraft) { commit(book().create(record)) }
    fun select(expectedActive:NativeDraft?,target:NativeDraft) {commit(book().select(expectedActive,target))}
    fun write(record: NativeDraft) {
        require(record.owner == owner)
        val current=book()
        // Preserve the initial-write contract for existing callers restoring an empty account.
        commit(if(current.active()==null)current.create(record) else current.update(record))
    }
    private fun commit(next:NativeDraftBook) {
        val plain = next.json().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size<=MAX_BYTES-4096)
            val packet = OfflineMailStore.crypt(true, key(true), "native-draft/v1/$owner", plain)
            require(packet.size<=MAX_BYTES)
            val output = file.startWrite()
            try { output.write(packet); file.finishWrite(output) } catch (error: Exception) { file.failWrite(output); throw error }
        } finally { plain.fill(0) }
    }
    fun clearSent(expected: NativeDraft) {
        remove(expected,NativeDraft.SENT)
    }
    fun clearScheduled(expected: NativeDraft) {
        remove(expected,NativeDraft.SCHEDULED)
    }
    fun discard(expected: NativeDraft) {
        remove(expected,NativeDraft.EDITING)
    }
    private fun remove(expected: NativeDraft,status: String) {
        require(expected.owner==owner)
        val next=book().remove(expected,status)
        if(next.active()!=null)commit(next) else {
            file.delete()
            check(listOf("", ".bak", ".new").none { File(file.baseFile.path+it).exists() })
        }
    }
    companion object {
        // Serialize account transactions across Activity instances, including rotation / duplicate launch.
        val gate = Any()
        private const val MAX_BYTES=32*1024*1024
        @Synchronized private fun key(create: Boolean): SecretKey {
            val alias = "spbu-native-drafts-v1"
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) return store.getKey(alias, null) as SecretKey
            check(create) { "Draft encryption key unavailable" }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
    }
}
