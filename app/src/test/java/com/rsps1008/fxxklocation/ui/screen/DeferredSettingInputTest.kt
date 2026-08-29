package com.rsps1008.fxxklocation.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredSettingInputTest {
    @Test
    fun transientInputIsKeptAndDoesNotWriteWhenItMatchesExternalValue() {
        val input = DeferredSettingInput<Double>()
        val savedValues = mutableListOf<Double>()

        input.onTextChanged("10.")
        input.commit(10.0, String::toDoubleOrNull) { savedValues += it }

        assertEquals("10.", input.text)
        assertTrue(savedValues.isEmpty())
    }

    @Test
    fun externalValueDoesNotOverwriteAnUncommittedEdit() {
        val input = DeferredSettingInput<Double>()

        input.onTextChanged("12")
        input.syncFromExternal(20.0) { value -> value.toInt().toString() }

        assertEquals("12", input.text)
    }

    @Test
    fun restoringACommittedDraftDoesNotWriteBeforeExternalStateLoads() {
        val input = DeferredSettingInput<Int>()
        val savedValues = mutableListOf<Int>()

        input.restoreText("20", hasPendingEdit = false)
        input.commit(10, String::toIntOrNull) { savedValues += it }

        assertEquals("20", input.text)
        assertTrue(savedValues.isEmpty())
    }

    @Test
    fun acknowledgedCommitKeepsTheOriginalTextFormatting() {
        val input = DeferredSettingInput<Double>()
        val savedValues = mutableListOf<Double>()

        input.onTextChanged("10.")
        input.commit(5.0, String::toDoubleOrNull) { savedValues += it }
        input.syncFromExternal(10.0) { value -> value.toInt().toString() }

        assertEquals(listOf(10.0), savedValues)
        assertEquals("10.", input.text)
        assertTrue(!input.hasPendingEdit)
    }

    @Test
    fun focusLossCommitsOnceAndDoesNotDuplicateThePendingWrite() {
        val input = DeferredSettingInput<Int>()
        val savedValues = mutableListOf<Int>()

        input.onTextChanged("15")
        input.onFocusChanged(
            isFocusedNow = true,
            externalValue = 10,
            format = Int::toString,
            parse = String::toIntOrNull,
            save = { savedValues += it }
        )
        input.onFocusChanged(
            isFocusedNow = false,
            externalValue = 10,
            format = Int::toString,
            parse = String::toIntOrNull,
            save = { savedValues += it }
        )
        input.commit(10, String::toIntOrNull) { savedValues += it }

        assertEquals(listOf(15), savedValues)
    }
}
