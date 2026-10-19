package com.example.fitapp.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object ManShapes {
    val Chip = RoundedCornerShape(10.dp)
    val Button = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
}

val AppShapes =
    Shapes(
        extraSmall = ManShapes.Chip,
        small = ManShapes.Chip,
        medium = ManShapes.Button,
        large = ManShapes.Card,
        extraLarge = ManShapes.Card,
    )
