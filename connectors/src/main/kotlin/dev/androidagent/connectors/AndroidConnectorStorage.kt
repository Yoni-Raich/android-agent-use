package dev.androidagent.connectors

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** SharedPreferences-backed non-secret connector state. */
class SharedPreferencesConnectorStateStore(
    context: Context,
    private val json: Json = ConnectorJson.default,
) : ConnectorStateStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun get(connectorId: String): ConnectorSnapshot? {
        val encoded = preferences.getString(storageKey(connectorId), null) ?: return null
        return try {
            json.decodeFromString<ConnectorSnapshot>(encoded)
        } catch (failure: Exception) {
            preferences.edit().remove(storageKey(connectorId)).apply()
            throw ConnectorStateCorruptException(connectorId, failure)
        }
    }

    override fun put(snapshot: ConnectorSnapshot) {
        require(snapshot.connectorId.isNotBlank()) { "Connector id must not be blank" }
        check(preferences.edit().putString(storageKey(snapshot.connectorId), json.encodeToString(snapshot)).commit()) {
            "Could not persist connector state"
        }
    }

    override fun remove(connectorId: String) {
        preferences.edit().remove(storageKey(connectorId)).apply()
    }

    private fun storageKey(connectorId: String): String {
        require(connectorId.matches(STORAGE_KEY_REGEX)) { "Invalid connector id" }
        return "$KEY_PREFIX$connectorId"
    }

    private companion object {
        const val PREFERENCES_NAME = "connector_state"
        const val KEY_PREFIX = "connector_"
        val STORAGE_KEY_REGEX = Regex("[a-zA-Z0-9._-]{1,80}")
    }
}

/**
 * AES-GCM vault backed by an Android Keystore AES key.
 *
 * Only the encrypted blob is placed in SharedPreferences. The Keystore key is
 * non-exportable and never leaves the Android Keystore provider.
 */
class AndroidKeystoreCredentialVault(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val json: Json = ConnectorJson.default,
) : CredentialVault {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    override fun read(key: String): CredentialBundle? {
        val encoded = preferences.getString(storageKey(key), null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_SIZE) { "Credential ciphertext is truncated" }
            val iv = packed.copyOfRange(0, IV_SIZE)
            val ciphertext = packed.copyOfRange(IV_SIZE, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            json.decodeFromString<CredentialBundle>(
                cipher.doFinal(ciphertext).toString(Charsets.UTF_8),
            )
        } catch (failure: Exception) {
            throw CredentialStoreCorruptException(key, failure)
        }
    }

    @Synchronized
    override fun write(key: String, credentials: CredentialBundle) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(json.encodeToString(credentials).toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + ciphertext
        check(
            preferences.edit()
                .putString(storageKey(key), Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit(),
        ) { "Could not persist encrypted credentials" }
    }

    @Synchronized
    override fun clear(key: String) {
        preferences.edit().remove(storageKey(key)).apply()
    }

    private fun storageKey(key: String): String {
        require(key.matches(STORAGE_KEY_REGEX)) { "Invalid credential key" }
        return "$KEY_PREFIX$key"
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_SIZE = 12
        const val DEFAULT_KEY_ALIAS = "android-agent.connector.credentials"
        const val PREFERENCES_NAME = "connector_credentials"
        const val KEY_PREFIX = "credential_"
        val STORAGE_KEY_REGEX = Regex("[a-zA-Z0-9._-]{1,80}")
    }
}

internal object ConnectorJson {
    val default: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }
}
