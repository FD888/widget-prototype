package com.vtbvita.widget.nlp

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-тесты для ContactMatcher — чистые функции без Android SDK.
 *
 * Покрывают:
 *  - maskPhone: маскирование номера телефона
 *  - isHighConfidence: порог уверенности при выборе контакта
 */
class ContactMatcherTest {

    // ── maskPhone ─────────────────────────────────────────────────────────────

    @Test
    fun `maskPhone masks 11-digit number correctly`() {
        val result = ContactMatcher.maskPhone("+79181234511")
        assertEquals("+7 (918) ***-**-11", result)
    }

    @Test
    fun `maskPhone masks 10-digit number without country code`() {
        val result = ContactMatcher.maskPhone("9181234567")
        // 10 digits → last 2 = "67"
        assertEquals("+7 (918) ***-**-67", result)
    }

    @Test
    fun `maskPhone returns original for short invalid number`() {
        val result = ContactMatcher.maskPhone("123")
        assertEquals("123", result)
    }

    @Test
    fun `maskPhone handles number with spaces and dashes`() {
        // +7 918 123-45-67 → digits = 79181234567 → 11 digits
        val result = ContactMatcher.maskPhone("+7 918 123-45-67")
        assertEquals("+7 (918) ***-**-67", result)
    }

    // ── isHighConfidence ──────────────────────────────────────────────────────

    @Test
    fun `isHighConfidence returns false for empty list`() {
        assertFalse(ContactMatcher.isHighConfidence(emptyList()))
    }

    @Test
    fun `isHighConfidence returns true for single candidate above threshold`() {
        val candidates = listOf(makeCandidate("Маша", score = 0.9f))
        assertTrue(ContactMatcher.isHighConfidence(candidates))
    }

    @Test
    fun `isHighConfidence returns false for single candidate below threshold`() {
        val candidates = listOf(makeCandidate("Маша", score = 0.3f))
        assertFalse(ContactMatcher.isHighConfidence(candidates))
    }

    @Test
    fun `isHighConfidence returns true when top candidate clearly wins`() {
        val candidates = listOf(
            makeCandidate("Маша Иванова", score = 0.95f),
            makeCandidate("Маша Петрова", score = 0.50f),
        )
        assertTrue(ContactMatcher.isHighConfidence(candidates))
    }

    @Test
    fun `isHighConfidence returns false when top two candidates are close`() {
        // gap = 0.85 - 0.75 = 0.10, требуется >= 0.30
        val candidates = listOf(
            makeCandidate("Маша Иванова", score = 0.85f),
            makeCandidate("Маша Петрова", score = 0.75f),
        )
        assertFalse(ContactMatcher.isHighConfidence(candidates))
    }

    @Test
    fun `isHighConfidence returns false when top score is below 0_8`() {
        // даже при большом gap — первый score должен быть >= 0.80
        val candidates = listOf(
            makeCandidate("Маша", score = 0.70f),
            makeCandidate("Паша", score = 0.10f),
        )
        assertFalse(ContactMatcher.isHighConfidence(candidates))
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeCandidate(name: String, score: Float) =
        ContactCandidate(
            displayName = name,
            phone = "+70000000000",
            bankDisplayName = name,
            score = score,
        )
}
