package com.aiagents.app.data.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseBootstrapTest {
    @Test
    fun initializationIsIdempotentAndSupportsFirebaseAuth() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = FirebaseBootstrap.ensureInitialized(context)
        val second = FirebaseBootstrap.ensureInitialized(context)

        assertSame(first, second)
        assertEquals(FirebaseApp.DEFAULT_APP_NAME, first.name)
        assertEquals(first.name, FirebaseAuth.getInstance(first).app.name)
    }
}
