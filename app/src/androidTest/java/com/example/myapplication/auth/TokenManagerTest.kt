package com.example.myapplication.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (not JVM unit) test: TokenManager persists to real on-device
 * SharedPreferences and, via SecureTokenStore, the real Android Keystore.
 */
@RunWith(AndroidJUnit4::class)
class TokenManagerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        TokenManager.clear(context)
    }

    @Test
    fun save_thenGetToken_returnsTheSameToken() {
        TokenManager.save(context, token = "abc123", name = "Jane", phone = "+966500000000", avatar = "", userId = "7")
        assertEquals("abc123", TokenManager.getToken(context))
        assertEquals("Jane", TokenManager.getName(context))
        assertEquals("+966500000000", TokenManager.getPhone(context))
        assertEquals("7", TokenManager.getUserId(context))
    }

    @Test
    fun isLoggedIn_reflectsSavedAndClearedState() {
        assertFalse(TokenManager.isLoggedIn(context))
        TokenManager.save(context, token = "abc123")
        assertTrue(TokenManager.isLoggedIn(context))
        TokenManager.clear(context)
        assertFalse(TokenManager.isLoggedIn(context))
    }

    @Test
    fun clear_removesToken() {
        TokenManager.save(context, token = "abc123")
        TokenManager.clear(context)
        assertNull(TokenManager.getToken(context))
    }

    @Test
    fun tokenIsStoredEncryptedOnDisk_notAsPlaintext() {
        // Regression test for the encrypted-token-storage change: guards against a
        // future edit accidentally reverting TokenManager to storing the raw token.
        val secretToken = "super-secret-bearer-token-xyz"
        TokenManager.save(context, token = secretToken)
        val rawPrefValue = context.getSharedPreferences("find_prefs", Context.MODE_PRIVATE)
            .getString("auth_token", null)
        assertNotEquals(secretToken, rawPrefValue)
    }
}