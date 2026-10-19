package com.example.fitapp.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.fitapp.core.designsystem.ManTheme

/** A label with a small line under it on the left and the number on the right. */
@Composable
fun StatRow(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = ManTheme.colors.muted)
        }
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
