package com.aiagents.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.aiagents.app.R
import com.aiagents.app.presentation.tool_results.ArtifactPresentation
import com.aiagents.app.presentation.tool_results.ResearchSource
import com.aiagents.app.presentation.tool_results.ToolActionReceipt
import com.aiagents.app.presentation.tool_results.UndoAction

@Composable
fun ToolActivityTimeline(
    receipts: List<ToolActionReceipt>,
    modifier: Modifier = Modifier,
    onUndo: ((UndoAction) -> Unit)? = null
) {
    if (receipts.isEmpty()) return
    var expanded by remember(receipts) {
        mutableStateOf(receipts.size <= 3 || receipts.any { it.status != ToolActionReceipt.Status.COMPLETED })
    }
    val completed = receipts.count { it.status == ToolActionReceipt.Status.COMPLETED }
    val summary = when {
        receipts.any { it.status == ToolActionReceipt.Status.FAILED } ->
            stringResource(R.string.tool_activity_attention)
        receipts.any { it.status == ToolActionReceipt.Status.NEEDS_USER } ->
            stringResource(R.string.tool_activity_waiting)
        receipts.any { it.status == ToolActionReceipt.Status.RUNNING } ->
            stringResource(R.string.tool_activity_running)
        completed == 1 -> stringResource(R.string.tool_activity_one_action, completed)
        else -> stringResource(R.string.tool_activity_actions, completed)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = summary,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.tool_activity_hide else R.string.tool_activity_show
                    ),
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    receipts.forEachIndexed { index, receipt ->
                        ReceiptRow(
                            receipt = receipt,
                            showConnector = index < receipts.lastIndex,
                            onUndo = onUndo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    receipt: ToolActionReceipt,
    showConnector: Boolean,
    onUndo: ((UndoAction) -> Unit)?
) {
    val statusColor = when (receipt.status) {
        ToolActionReceipt.Status.RUNNING -> MaterialTheme.colorScheme.primary
        ToolActionReceipt.Status.COMPLETED -> Color(0xFF2E7D32)
        ToolActionReceipt.Status.NEEDS_USER -> Color(0xFFF57C00)
        ToolActionReceipt.Status.FAILED -> MaterialTheme.colorScheme.error
    }
    val statusIcon = when (receipt.status) {
        ToolActionReceipt.Status.RUNNING -> Icons.Default.Pending
        ToolActionReceipt.Status.COMPLETED -> Icons.Default.CheckCircle
        ToolActionReceipt.Status.NEEDS_USER -> Icons.Default.TouchApp
        ToolActionReceipt.Status.FAILED -> Icons.Default.Error
    }
    val statusText = when (receipt.status) {
        ToolActionReceipt.Status.RUNNING -> stringResource(R.string.tool_status_running)
        ToolActionReceipt.Status.COMPLETED -> stringResource(R.string.tool_status_completed)
        ToolActionReceipt.Status.NEEDS_USER -> stringResource(R.string.tool_status_confirm)
        ToolActionReceipt.Status.FAILED -> stringResource(
            if (receipt.permission == ToolActionReceipt.Permission.DENIED) {
                R.string.tool_status_permission_denied
            } else {
                R.string.tool_status_failed
            }
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusText,
                modifier = Modifier.size(18.dp),
                tint = statusColor
            )
            if (showConnector) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .width(1.dp)
                        .height(31.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = receipt.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(text = statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            if (receipt.summary.isNotBlank()) {
                Text(
                    text = receipt.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (receipt.undo != null && onUndo != null) {
                TextButton(onClick = { onUndo(receipt.undo) }) {
                    Text(receipt.undo.confirmation)
                }
            }
        }
    }
}

@Composable
fun ResearchSourcesCard(
    sources: List<ResearchSource>,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.50f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.research_sources_count, sources.size),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sources.forEach { source ->
                    SourceFaviconChip(source) { runCatching { uriHandler.openUri(source.url) } }
                }
            }
        }
    }
}

@Composable
private fun SourceFaviconChip(source: ResearchSource, onOpen: () -> Unit) {
    val context = LocalContext.current
    var faviconFailed by remember(source.url) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(domainColor(source.domain))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "${source.title}. ${source.domain}" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = source.domain.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clearAndSetSemantics { }
        )
        if (!faviconFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://www.google.com/s2/favicons?domain=${source.domain}&sz=64")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clearAndSetSemantics { },
                contentScale = ContentScale.Fit,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) faviconFailed = true
                }
            )
        }
    }
}

private fun domainColor(domain: String): Color {
    val hue = (domain.hashCode() and 0x7FFFFFFF) % 360
    return Color.hsl(hue.toFloat(), 0.55f, 0.42f)
}

@Composable
fun ArtifactCard(
    artifact: ArtifactPresentation,
    canOpen: Boolean,
    canShare: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = artifactIcon(artifact.kind)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${artifact.detail}: ${artifact.name}" },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.46f))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(artifact.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(artifact.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canOpen) {
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Filled.Launch,
                        contentDescription = stringResource(R.string.artifact_open, artifact.name)
                    )
                }
            }
            if (canShare) {
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = stringResource(R.string.artifact_share, artifact.name)
                    )
                }
            }
        }
    }
}

@Composable
fun CapabilityStrip(
    selectedModel: String,
    supportsVision: Boolean,
    supportsDocuments: Boolean,
    waitingForPermission: Boolean,
    modifier: Modifier = Modifier
) {
    if (selectedModel.isBlank()) return
    val provider = selectedModel.substringBefore('|').uppercase()
    val (modeLabel, modeIcon) = when (provider) {
        "LOCAL" -> stringResource(R.string.capability_on_device) to Icons.Default.Memory
        "OLLAMA", "LM_STUDIO" -> stringResource(R.string.capability_local_network) to Icons.Default.Storage
        else -> stringResource(R.string.capability_cloud) to Icons.Default.Cloud
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CapabilityPill(modeIcon, modeLabel)
        if (supportsVision) CapabilityPill(Icons.Default.Image, stringResource(R.string.capability_images))
        if (supportsDocuments) CapabilityPill(Icons.Default.AttachFile, stringResource(R.string.capability_files))
        if (waitingForPermission) CapabilityPill(
            Icons.Default.TouchApp,
            stringResource(R.string.capability_permission_pending),
            MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CapabilityPill(icon: ImageVector, label: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

private fun artifactIcon(kind: ArtifactPresentation.Kind): ImageVector = when (kind) {
    ArtifactPresentation.Kind.CODE -> Icons.Default.Code
    ArtifactPresentation.Kind.DATA -> Icons.Default.InsertChart
    ArtifactPresentation.Kind.DOCUMENT -> Icons.Default.Description
    ArtifactPresentation.Kind.PRESENTATION -> Icons.Default.InsertChart
    ArtifactPresentation.Kind.IMAGE -> Icons.Default.Image
    ArtifactPresentation.Kind.REMOTE -> Icons.Default.Cloud
    ArtifactPresentation.Kind.FILE -> Icons.Default.AttachFile
}
