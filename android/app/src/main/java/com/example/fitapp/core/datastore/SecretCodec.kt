package com.example.fitapp.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Reversible string encryption. Split out so storage logic can be tested without a Keystore. */
interface SecretCodec {
    fun encrypt(plain: String): String

    /** Returns null if the value cannot be decrypted (for example the key was invalidated). */
    fun decrypt(encoded: String): String?
}

/** AES-256-GCM with a non-exportable key in the Android Keystore. Output is Base64(iv + ciphertext). */
@Singleton
class KeystoreSecretCodec
    @Inject
    constructor() : SecretCodec {
        private fun key(): SecretKey {
            val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            return generator.generateKey()
        }

        override fun encrypt(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(iv + sealed)
        }

        override fun decrypt(encoded: String): String? =
            runCatching {
                val bytes = Base64.getDecoder().decode(encoded)
                val iv = bytes.copyOfRange(0, IV_BYTES)
                val sealed = bytes.copyOfRange(IV_BYTES, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(sealed), Charsets.UTF_8)
            }.getOrNull()

        private companion object {
            const val PROVIDER = "AndroidKeyStore"
            const val ALIAS = "the_man_project_token_key"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val KEY_BITS = 256
            const val IV_BYTES = 12
            const val TAG_BITS = 128
        }
    }
