package com.aiagents.app.data.terminal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.aiagents.app.domain.model.AssistantActionResult
import com.aiagents.app.domain.model.AssistantActionStatus
import com.aiagents.app.domain.model.AssistantContactCandidate
import com.aiagents.app.domain.model.ContactActionPurpose
import com.aiagents.app.domain.model.PendingAssistantAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.UUID
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AssistantActionCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val whatsAppHandoffBackend: WhatsAppHandoffBackend
) {
    private val _pendingAction = MutableStateFlow<PendingAssistantAction?>(null)
    val pendingAction: StateFlow<PendingAssistantAction?> = _pendingAction.asStateFlow()

    fun reset() {
        _pendingAction.value = null
    }

    fun prepareWhatsApp(contactQuery: String, message: String): AssistantActionResult {
        if (!SafeAppIntentPolicy.isValidAppName(contactQuery)) {
            return AssistantActionResult(false, "El contacto no es válido.")
        }
        if (!SafeAppIntentPolicy.isValidShareText(message)) {
            return AssistantActionResult(false, "El mensaje está vacío o es demasiado largo.")
        }
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return AssistantActionResult(false, "Se necesita permiso de contactos para encontrar a la persona.")
        }

        val candidates = resolveContacts(contactQuery)
        if (candidates.isEmpty()) {
            return AssistantActionResult(false, "No encontré un contacto llamado $contactQuery.")
        }
        val expiresAt = System.currentTimeMillis() + ACTION_TTL_MS
        _pendingAction.value = if (candidates.size == 1) {
            PendingAssistantAction.WhatsAppDraft(
                id = UUID.randomUUID().toString(),
                contact = candidates.single(),
                message = message,
                expiresAt = expiresAt
            )
        } else {
            PendingAssistantAction.ContactSelection(
                id = UUID.randomUUID().toString(),
                purpose = ContactActionPurpose.WHATSAPP,
                query = contactQuery,
                candidates = candidates,
                message = message,
                expiresAt = expiresAt
            )
        }
        return if (candidates.size == 1) {
            AssistantActionResult(
                true,
                "Borrador para ${candidates.single().displayName} preparado. ¿Lo envío por WhatsApp?"
            )
        } else {
            AssistantActionResult(
                true,
                "Encontré varios contactos para $contactQuery. Elige uno en la tarjeta del asistente."
            )
        }
    }

    fun callPhone(contactQuery: String?, rawNumber: String?): AssistantActionResult {
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            return AssistantActionResult(false, "Se necesita permiso de teléfono para iniciar la llamada.")
        }
        val directNumber = rawNumber?.let(SafeAppIntentPolicy::normalizePhoneNumber)
        if (rawNumber != null && directNumber == null) {
            return AssistantActionResult(false, "El número de teléfono no es válido.")
        }
        if (directNumber != null) return launchCall(directNumber)

        val query = contactQuery?.trim().orEmpty()
        if (!SafeAppIntentPolicy.isValidAppName(query)) {
            return AssistantActionResult(false, "Indica un contacto o un número de teléfono.")
        }
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return AssistantActionResult(false, "Se necesita permiso de contactos para encontrar a la persona.")
        }
        val candidates = resolveContacts(query)
        if (candidates.isEmpty()) {
            return AssistantActionResult(false, "No encontré un contacto llamado $query.")
        }
        if (candidates.size == 1) return launchCall(candidates.single().phoneNumber)

        _pendingAction.value = PendingAssistantAction.ContactSelection(
            id = UUID.randomUUID().toString(),
            purpose = ContactActionPurpose.CALL,
            query = query,
            candidates = candidates,
            expiresAt = System.currentTimeMillis() + ACTION_TTL_MS
        )
        return AssistantActionResult(true, "Encontré varios números. Elige uno en la tarjeta del asistente.")
    }

    fun selectContact(actionId: String, candidateId: String): AssistantActionResult {
        val selection = _pendingAction.value as? PendingAssistantAction.ContactSelection
            ?: return AssistantActionResult(false, "La selección ya no está disponible.")
        if (selection.id != actionId || selection.isExpired()) {
            expire(actionId)
            return AssistantActionResult(false, "La selección expiró. Pide la acción nuevamente.")
        }
        val candidate = selection.candidates.firstOrNull { it.id == candidateId }
            ?: return AssistantActionResult(false, "El contacto seleccionado ya no está disponible.")
        return when (selection.purpose) {
            ContactActionPurpose.CALL -> {
                _pendingAction.value = null
                launchCall(candidate.phoneNumber)
            }
            ContactActionPurpose.WHATSAPP -> {
                _pendingAction.value = PendingAssistantAction.WhatsAppDraft(
                    id = UUID.randomUUID().toString(),
                    contact = candidate,
                    message = selection.message.orEmpty(),
                    expiresAt = System.currentTimeMillis() + ACTION_TTL_MS
                )
                AssistantActionResult(true, "Contacto seleccionado. Confirma el mensaje de WhatsApp.")
            }
        }
    }

    fun updateWhatsAppMessage(actionId: String, message: String) {
        val draft = _pendingAction.value as? PendingAssistantAction.WhatsAppDraft ?: return
        if (draft.id == actionId && draft.status == AssistantActionStatus.DRAFT) {
            _pendingAction.value = draft.copy(message = message.take(SafeAppIntentPolicy.MAX_SHARE_TEXT_LENGTH))
        }
    }

    fun confirmWhatsApp(actionId: String, editedMessage: String? = null): AssistantActionResult {
        val draft = _pendingAction.value as? PendingAssistantAction.WhatsAppDraft
            ?: return AssistantActionResult(false, "El borrador ya no está disponible.")
        if (draft.id != actionId || draft.isExpired()) {
            expire(actionId)
            return AssistantActionResult(false, "El borrador expiró. Pide el mensaje nuevamente.")
        }
        val message = editedMessage ?: draft.message
        if (!SafeAppIntentPolicy.isValidShareText(message)) {
            return AssistantActionResult(false, "Escribe un mensaje antes de abrir WhatsApp.")
        }
        val digits = whatsappDigits(draft.contact.phoneNumber)
        if (digits.length < 3) {
            return failDraft(draft, "El contacto no tiene un número válido para WhatsApp.")
        }
        val handoff = whatsAppHandoffBackend.openPreparedChat(digits, message)
        return if (handoff.success) {
            _pendingAction.value = draft.copy(
                message = message,
                status = AssistantActionStatus.HANDED_OFF
            )
            handoff
        } else {
            failDraft(draft, handoff.message)
        }
    }

    fun cancel(actionId: String) {
        when (val action = _pendingAction.value) {
            is PendingAssistantAction.WhatsAppDraft -> if (action.id == actionId) {
                _pendingAction.value = action.copy(status = AssistantActionStatus.CANCELLED)
            }
            is PendingAssistantAction.ContactSelection -> if (action.id == actionId) {
                _pendingAction.value = action.copy(status = AssistantActionStatus.CANCELLED)
            }
            null -> Unit
        }
    }

    fun expire(actionId: String) {
        when (val action = _pendingAction.value) {
            is PendingAssistantAction.WhatsAppDraft -> if (
                action.id == actionId && action.status == AssistantActionStatus.DRAFT &&
                System.currentTimeMillis() >= action.expiresAt
            ) {
                _pendingAction.value = action.copy(status = AssistantActionStatus.EXPIRED)
            }
            is PendingAssistantAction.ContactSelection -> if (
                action.id == actionId && action.status == AssistantActionStatus.DRAFT &&
                System.currentTimeMillis() >= action.expiresAt
            ) {
                _pendingAction.value = action.copy(status = AssistantActionStatus.EXPIRED)
            }
            null -> Unit
        }
    }

    fun clearTerminalAction() {
        if (_pendingAction.value?.status != AssistantActionStatus.DRAFT) _pendingAction.value = null
    }

    private fun launchCall(phoneNumber: String): AssistantActionResult {
        @Suppress("DEPRECATION")
        val isEmergency = PhoneNumberUtils.isEmergencyNumber(phoneNumber)
        if (isEmergency) {
            return AssistantActionResult(false, "Por seguridad, confirma y marca manualmente los números de emergencia.")
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AssistantActionResult(true, "Llamando al número seleccionado.")
        } catch (_: SecurityException) {
            AssistantActionResult(false, "Android no concedió permiso para realizar la llamada.")
        } catch (_: Exception) {
            AssistantActionResult(false, "No hay una aplicación de teléfono disponible.")
        }
    }

    private fun resolveContacts(query: String): List<AssistantContactCandidate> {
        val normalizedQuery = query.normalizedForSearch()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )
        val rows = mutableListOf<AssistantContactCandidate>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(projection[0])
                val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
                val numberIndex = cursor.getColumnIndexOrThrow(projection[2])
                val typeIndex = cursor.getColumnIndexOrThrow(projection[3])
                val labelIndex = cursor.getColumnIndexOrThrow(projection[4])
                val photoIndex = cursor.getColumnIndexOrThrow(projection[5])
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex).orEmpty()
                    val searchable = name.normalizedForSearch()
                    if (searchable != normalizedQuery && !searchable.contains(normalizedQuery)) continue
                    val number = SafeAppIntentPolicy.normalizePhoneNumber(cursor.getString(numberIndex).orEmpty())
                        ?: continue
                    val type = cursor.getInt(typeIndex)
                    val customLabel = cursor.getString(labelIndex)
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel
                    ).toString()
                    rows += AssistantContactCandidate(
                        id = "${cursor.getLong(idIndex)}:$number",
                        displayName = name,
                        phoneNumber = number,
                        label = label,
                        photoUri = cursor.getString(photoIndex)
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        val exact = rows.filter { it.displayName.normalizedForSearch() == normalizedQuery }
        return (exact.ifEmpty { rows })
            .distinctBy { it.displayName.normalizedForSearch() to it.phoneNumber }
            .take(MAX_CONTACT_CHOICES)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun whatsappDigits(phoneNumber: String): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val countryIso = telephonyManager?.networkCountryIso
            ?.takeIf(String::isNotBlank)
            ?: telephonyManager?.simCountryIso?.takeIf(String::isNotBlank)
            ?: Locale.getDefault().country
        val e164 = countryIso.takeIf(String::isNotBlank)?.let { country ->
            PhoneNumberUtils.formatNumberToE164(phoneNumber, country.uppercase(Locale.ROOT))
        }
        return (e164 ?: phoneNumber).filter(Char::isDigit)
    }

    private fun PendingAssistantAction.isExpired(): Boolean =
        System.currentTimeMillis() >= expiresAt || status != AssistantActionStatus.DRAFT

    private fun failDraft(
        draft: PendingAssistantAction.WhatsAppDraft,
        message: String
    ): AssistantActionResult {
        _pendingAction.value = draft.copy(
            status = AssistantActionStatus.FAILED,
            failureMessage = message
        )
        return AssistantActionResult(false, message)
    }

    private fun String.normalizedForSearch(): String = Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .trim()

    companion object {
        private const val ACTION_TTL_MS = 2 * 60 * 1_000L
        private const val MAX_CONTACT_CHOICES = 8
    }
}
