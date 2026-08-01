package com.example.myapplication.utils

import com.example.myapplication.chat.model.BlockedUserDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerationStateTest {

    // ModerationState is a process-wide singleton — reset it after every test so
    // state from one test can't leak into the next.
    @After
    fun tearDown() {
        ModerationState.reset()
    }

    private fun user(id: Int, name: String = "User$id") =
        BlockedUserDto(id = id, name = name, avatar = null, blockedAt = null)

    @Test
    fun isBlocked_freshState_returnsFalse() {
        assertFalse(ModerationState.isBlocked(1))
    }

    @Test
    fun isBlocked_nullId_returnsFalse() {
        assertFalse(ModerationState.isBlocked(null))
    }

    @Test
    fun markBlocked_setsIsBlockedTrue_andAddsToCachedList() {
        ModerationState.markBlocked(user(42))
        assertTrue(ModerationState.isBlocked(42))
        assertEquals(1, ModerationState.cached().size)
        assertEquals(42, ModerationState.cached().first().id)
    }

    @Test
    fun markBlocked_sameUserTwice_doesNotDuplicate() {
        ModerationState.markBlocked(user(42, "Old name"))
        ModerationState.markBlocked(user(42, "New name"))
        assertEquals(1, ModerationState.cached().size)
        assertEquals("New name", ModerationState.cached().first().name)
    }

    @Test
    fun markUnblocked_removesUser_andClearsIsBlocked() {
        ModerationState.markBlocked(user(1))
        ModerationState.markBlocked(user(2))
        ModerationState.markUnblocked(1)
        assertFalse(ModerationState.isBlocked(1))
        assertTrue(ModerationState.isBlocked(2))
        assertEquals(1, ModerationState.cached().size)
    }

    @Test
    fun reset_clearsAllBlockedState() {
        ModerationState.markBlocked(user(1))
        ModerationState.reset()
        assertFalse(ModerationState.isBlocked(1))
        assertTrue(ModerationState.cached().isEmpty())
    }
}