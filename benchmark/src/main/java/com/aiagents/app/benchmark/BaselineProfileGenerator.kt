package com.aiagents.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures startup code paths for release builds.
 *
 * Run on an API 33+ physical device while it is not being used by another adb task, then copy the
 * generated profile into the app module before comparing startup benchmarks.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.aiagents.app"
    }
}
