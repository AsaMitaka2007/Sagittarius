package com.dsha.sagittarius.agent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 每个 provider 独立 Key，使用 Android Keystore 加密，DataStore 只保存 IV 与密文。 */
class ApiKeyStore(private val context: Context) {

    private fun cipherKey(provider: String) = stringPreferencesKey("api_key_cipher_$provider")
    private fun ivKey(provider: String) = stringPreferencesKey("api_key_iv_$provider")
    private fun alias(provider: String) = "sagittarius_api_key_$provider"

    suspend fun save(provider: String, key: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(provider))
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        context.sagittariusDataStore.edit {
            it[cipherKey(provider)] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            it[ivKey(provider)] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            it[AgentPrefsKeys.ONBOARDED] = true
        }
    }

    suspend fun load(provider: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = context.sagittariusDataStore.data.first()
            val encrypted = prefs[cipherKey(provider)] ?: return@runCatching null
            val iv = prefs[ivKey(provider)] ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(provider),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse {
            clear(provider)
            null
        }
    }

    suspend fun clear(provider: String) = withContext(Dispatchers.IO) {
        context.sagittariusDataStore.edit {
            it.remove(cipherKey(provider))
            it.remove(ivKey(provider))
        }
    }

    private fun secretKey(provider: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias(provider), null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias(provider), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }
}
