package com.aiagents.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiagents.app.presentation.tool_results.DirectToolResultPayload

/** Native, glass-styled rendering for verified direct tool results. */
@Composable
fun DirectToolResultCard(
    payload: DirectToolResultPayload,
    modifier: Modifier = Modifier
) {
    payload.weatherJson?.let { weatherJson ->
        WeatherResultCard(weatherJson = weatherJson, modifier = modifier)
        return
    }

    val colors = directResultColors(payload.kind)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.20f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "${payload.title}. ${payload.summary}"
            }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResultOrb(colors = colors)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = payload.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                if (payload.summary.isNotBlank()) {
                    Text(
                        text = payload.summary,
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultOrb(colors: List<Color>) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.38f), colors.last())))
            .border(1.dp, Color.White.copy(alpha = 0.34f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.86f))
        )
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(colors.first())
        )
    }
}

private fun directResultColors(kind: DirectToolResultPayload.Kind): List<Color> = when (kind) {
    DirectToolResultPayload.Kind.REMINDER -> listOf(Color(0xFF5E35B1), Color(0xFF7B1FA2))
    DirectToolResultPayload.Kind.CALENDAR -> listOf(Color(0xFF1565C0), Color(0xFF3949AB))
    DirectToolResultPayload.Kind.DEVICE_ACTION -> listOf(Color(0xFF00695C), Color(0xFF283593))
    DirectToolResultPayload.Kind.WEATHER -> listOf(Color(0xFF0277BD), Color(0xFF29B6F6))
}
