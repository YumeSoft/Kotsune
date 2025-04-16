package me.thuanc177.kotsune.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun EpisodePagination(
    currentPage: Int,
    totalPages: Int,
    onPageSelected: (Int) -> Unit
) {
    if (totalPages <= 1) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous page button
        IconButton(
            onClick = { if (currentPage > 0) onPageSelected(currentPage - 1) },
            enabled = currentPage > 0,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Previous page"
            )
        }

        // Page numbers
        val visiblePages = 5 // Number of page buttons to show
        val startPage = maxOf(0, minOf(currentPage - visiblePages / 2, totalPages - visiblePages))
        val endPage = minOf(startPage + visiblePages, totalPages)

        for (page in startPage until endPage) {
            OutlinedButton(
                onClick = { onPageSelected(page) },
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (page == currentPage)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        Color.Transparent
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (page == currentPage)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = "${page + 1}",
                    color = if (page == currentPage)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Next page button
        IconButton(
            onClick = { if (currentPage < totalPages - 1) onPageSelected(currentPage + 1) },
            enabled = currentPage < totalPages - 1,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next page"
            )
        }
    }
}