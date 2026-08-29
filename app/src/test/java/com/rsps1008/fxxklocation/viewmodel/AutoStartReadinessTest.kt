package com.rsps1008.fxxklocation.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartReadinessTest {
    @Test
    fun autoStartIsAllowedWhenEveryRequiredConditionIsReady() {
        assertTrue(eligible())
    }

    @Test
    fun autoStartIsRejectedBeforeInitialDataOrStatusIsReady() {
        assertFalse(eligible(initialLocationLoaded = false))
        assertFalse(eligible(systemStatusChecked = false))
    }

    @Test
    fun autoStartIsRejectedWhenMockIsAlreadyRunningOrApplied() {
        assertFalse(eligible(isMocking = true))
        assertFalse(eligible(isApplied = true))
        assertFalse(eligible(hasRuntimeMockLocation = true))
    }

    @Test
    fun autoStartIsRejectedWhenAnOriginalRequirementIsMissing() {
        assertFalse(eligible(autoStartEnabled = false))
        assertFalse(eligible(isMockAppSet = false))
        assertFalse(eligible(hasPermission = false))
        assertFalse(eligible(isIgnoringBatteryOptimizations = false))
        assertFalse(eligible(hasNotificationPermission = false))
    }

    private fun eligible(
        autoStartEnabled: Boolean = true,
        initialLocationLoaded: Boolean = true,
        systemStatusChecked: Boolean = true,
        isMocking: Boolean = false,
        isApplied: Boolean = false,
        hasRuntimeMockLocation: Boolean = false,
        isMockAppSet: Boolean = true,
        hasPermission: Boolean = true,
        isIgnoringBatteryOptimizations: Boolean = true,
        hasNotificationPermission: Boolean = true
    ): Boolean = canAutoStart(
        autoStartEnabled = autoStartEnabled,
        initialLocationLoaded = initialLocationLoaded,
        systemStatusChecked = systemStatusChecked,
        isMocking = isMocking,
        isApplied = isApplied,
        hasRuntimeMockLocation = hasRuntimeMockLocation,
        isMockAppSet = isMockAppSet,
        hasPermission = hasPermission,
        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
        hasNotificationPermission = hasNotificationPermission
    )
}
