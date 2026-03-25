package com.aiagents.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object ShapeTokens {
    val CornerSmall = RoundedCornerShape(4.dp)
    val CornerMedium = RoundedCornerShape(8.dp)
    val CornerLarge = RoundedCornerShape(16.dp)
    val CornerExtraLarge = RoundedCornerShape(28.dp)
    val CornerFull = RoundedCornerShape(50)
    
    val None = RoundedCornerShape(0.dp)
    val SmallTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val SmallBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    val MediumTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val MediumBottom = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    
    val Pill = RoundedCornerShape(50)
    val Card = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(8.dp)
    val TextField = RoundedCornerShape(12.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val FloatingActionButton = RoundedCornerShape(16.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val NavigationBar = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    
    val MessageUser = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 4.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )
    val MessageAssistant = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )
    val MessageSystem = RoundedCornerShape(12.dp)
}

val ExpressiveShapes = Shapes(
    small = ShapeTokens.CornerMedium,
    medium = ShapeTokens.CornerLarge,
    large = ShapeTokens.CornerExtraLarge,
    extraLarge = RoundedCornerShape(28.dp)
)
