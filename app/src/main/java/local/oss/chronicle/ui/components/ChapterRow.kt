package local.oss.chronicle.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import local.oss.chronicle.data.model.Chapter

/**
 * A single chapter row for [local.oss.chronicle.ui.screens.NowPlayingScreen]'s chapter list.
 * Replaces the phone's finger-drag chapter seek with a plain tap-to-jump list entry (PLAN.md 5.4:
 * "No finger-drag seek: skip +/-N plus chapter jump").
 */
@Composable
fun ChapterRow(
    chapter: Chapter,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Chip(
        onClick = onClick,
        modifier = modifier,
        colors = if (isActive) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        label = {
            Text(
                text = chapter.title.ifBlank { "Chapter ${chapter.index + 1}" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(text = chapter.durationStr, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )
}
