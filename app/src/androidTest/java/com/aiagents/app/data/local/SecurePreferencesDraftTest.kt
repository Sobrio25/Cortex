package com.aiagents.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePreferencesDraftTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun cleanUp() {
        SecurePreferences(context).clearDraft(FIRST_WORKSPACE)
        SecurePreferences(context).clearDraft(SECOND_WORKSPACE)
    }

    @Test
    fun draftPersistsAcrossInstancesAndRemainsScopedToItsWorkspace() {
        val writer = SecurePreferences(context)
        writer.clearDraft(FIRST_WORKSPACE)
        writer.clearDraft(SECOND_WORKSPACE)
        writer.saveDraft(FIRST_WORKSPACE, "Mensaje aún no enviado")

        val restoredAfterScreenRecreation = SecurePreferences(context)

        assertEquals("Mensaje aún no enviado", restoredAfterScreenRecreation.getDraft(FIRST_WORKSPACE))
        assertEquals("", restoredAfterScreenRecreation.getDraft(SECOND_WORKSPACE))
    }

    @Test
    fun clearingSentDraftPreventsItFromBeingRestored() {
        val preferences = SecurePreferences(context)
        preferences.saveDraft(FIRST_WORKSPACE, "Mensaje ya enviado")

        preferences.clearDraft(FIRST_WORKSPACE)

        assertEquals("", SecurePreferences(context).getDraft(FIRST_WORKSPACE))
    }

    private companion object {
        const val FIRST_WORKSPACE = 9_000_001L
        const val SECOND_WORKSPACE = 9_000_002L
    }
}
