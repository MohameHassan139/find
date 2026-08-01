package com.example.myapplication.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (not JVM unit) test: encrypt/decrypt round-trips through the real
 * Android Keystore, which only exists on-device.
 */
@RunWith(AndroidJUnit4::class)
class SecureTokenStoreTest {

    @Test
    fun encryptDecrypt_roundTrips_originalValue() {
        val original = "test-bearer-token-12345"
        val encrypted = SecureTokenStore.encrypt(original)
        val decrypted = SecureTokenStore.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun encrypt_doesNotStorePlaintext() {
        val original = "super-secret-token"
        val encrypted = SecureTokenStore.encrypt(original)
        assertNotEquals(original, encrypted)
        assertFalse(encrypted.contains(original))
    }

    @Test
    fun decrypt_garbageInput_returnsNullInsteadOfCrashing() {
        assertNull(SecureTokenStore.decrypt("not-valid-base64-or-ciphertext"))
    }

    @Test
    fun encrypt_sameInputTwice_producesDifferentCiphertext() {
        // GCM uses a random IV per call — encrypting the same plaintext twice should
        // never produce identical ciphertext (a fixed/reused IV would be a serious
        // crypto bug: it lets an attacker who sees two ciphertexts XOR out key material).
        val a = SecureTokenStore.encrypt("same-value")
        val b = SecureTokenStore.encrypt("same-value")
        assertNotEquals(a, b)
    }
}