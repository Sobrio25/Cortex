package com.aiagents.app.data.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aiagents.app.domain.model.AssistantActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface WhatsAppHandoffBackend {
    fun openPreparedChat(phoneDigits: String, message: String): AssistantActionResult
}

@Singleton
class IntentWhatsAppHandoffBackend @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WhatsAppHandoffBackend {
    override fun openPreparedChat(phoneDigits: String, message: String): AssistantActionResult {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$phoneDigits?text=${Uri.encode(message)}")
        ).apply {
            setPackage(WHATSAPP_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AssistantActionResult(
                true,
                "WhatsApp se abrió con el mensaje preparado. Revisa y pulsa enviar allí."
            )
        } catch (_: Exception) {
            AssistantActionResult(false, "WhatsApp no está instalado o no pudo abrir el chat.")
        }
    }

    private companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
    }
}
