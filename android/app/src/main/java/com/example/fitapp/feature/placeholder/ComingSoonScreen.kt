package com.example.fitapp.feature.placeholder

import androidx.compose.runtime.Composable
import com.example.fitapp.core.designsystem.components.EmptyState

/** Stand-in for tabs whose features land in later phases. */
@Composable
fun ComingSoonScreen(
    title: String,
    message: String,
) {
    EmptyState(title = title, message = message)
}
