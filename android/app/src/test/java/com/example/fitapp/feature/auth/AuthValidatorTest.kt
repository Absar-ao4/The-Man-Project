package com.example.fitapp.feature.auth

import com.example.fitapp.feature.auth.domain.AuthValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {
    @Test
    fun `accepts normal emails and rejects malformed ones`() {
        assertNull(AuthValidator.email("alice@example.com"))
        assertNull(AuthValidator.email("  alice+gym@mail.example.org "))
        assertNotNull(AuthValidator.email(""))
        assertNotNull(AuthValidator.email("alice"))
        assertNotNull(AuthValidator.email("alice@"))
        assertNotNull(AuthValidator.email("alice@example"))
        assertNotNull(AuthValidator.email("a b@example.com"))
    }

    @Test
    fun `password length matches the backend limits`() {
        assertEquals("Use at least 8 characters", AuthValidator.password("short"))
        assertNull(AuthValidator.password("12345678"))
        assertNull(AuthValidator.password("x".repeat(72)))
        assertEquals("Use at most 72 characters", AuthValidator.password("x".repeat(73)))
    }

    @Test
    fun `login only requires a non empty password`() {
        assertNotNull(AuthValidator.loginPassword(""))
        assertNull(AuthValidator.loginPassword("a"))
    }

    @Test
    fun `username allows letters digits and underscores 3 to 20 long`() {
        assertNull(AuthValidator.username("alice_99"))
        assertNotNull(AuthValidator.username("ab"))
        assertNotNull(AuthValidator.username("a".repeat(21)))
        assertNotNull(AuthValidator.username("has space"))
        assertNotNull(AuthValidator.username("dash-ed"))
    }

    @Test
    fun `name must not be blank`() {
        assertNotNull(AuthValidator.name("   "))
        assertNull(AuthValidator.name("Alice"))
        assertNotNull(AuthValidator.name("x".repeat(81)))
    }
}
