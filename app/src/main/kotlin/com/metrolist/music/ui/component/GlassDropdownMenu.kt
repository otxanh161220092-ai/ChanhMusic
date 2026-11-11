package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

private val DestructiveColor = Color(0xFFFF3B30)

/**
 * One item in a [GlassDropdownMenu].
 *
 * - [isHeader]: a section title, small and muted, not clickable.
 * - [isDivider]: a thin separator line.
 * - [icon] draws on the left; [shortcut] text draws on the right.
 * - [selected] adds a radio mark (null hides it); [hasSubmenu] adds a chevron.
 * - [destructive] colours the row red; [enabled] = false greys it out.
 */
data class GlassMenuEntry(
    val label: String,
    val onClick: () -> Unit = {},
    @DrawableRes val icon: Int? = null,
    val selected: Boolean? = null,
    val shortcut: String? = null,
    val hasSubmenu: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val isHeader: Boolean = false,
    val isDivider: Boolean = false,
)

/** One icon button in the top row of a [GlassDropdownMenu]. */
data class GlassTopAction(
    val label: String,
    @DrawableRes val icon: Int,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Dropdown menu. It uses the same popup as the three-dot menus ([BottomSheetMenu]):
 * liquid glass anchored at the tapped position, closed by an outside tap or Back.
 * Callers keep the same API; `expanded` and `onDismissRequest` still drive it.
 */
@Composable
fun BoxScope.GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<GlassMenuEntry>,
    modifier: Modifier = Modifier,
    topActions: List<GlassTopAction> = emptyList(),
) {
    val menu = LocalMenuState.current
    val entriesState = rememberUpdatedState(entries)
    val dismissState = rememberUpdatedState(onDismissRequest)
    val expandedState = rememberUpdatedState(expanded)
    val topActionsState = rememberUpdatedState(topActions)

    // Open or close the shared popup to match `expanded`.
    LaunchedEffect(expanded) {
        if (expanded) {
            menu.show {
                GlassMenuList(
                    entries = entriesState.value,
                    topActions = topActionsState.value,
                    onEntry = { entry ->
                        entry.onClick()
                        dismissState.value()
                    },
                    onTopAction = { action ->
                        action.onClick()
                        dismissState.value()
                    },
                )
            }
        } else if (menu.isVisible) {
            menu.dismiss()
        }
    }

    // The popup was closed by an outside tap or Back: tell the caller.
    LaunchedEffect(menu.isVisible) {
        if (!menu.isVisible && expandedState.value) {
            dismissState.value()
        }
    }
}

@Composable
private fun GlassMenuList(
    entries: List<GlassMenuEntry>,
    topActions: List<GlassTopAction>,
    onEntry: (GlassMenuEntry) -> Unit,
    onTopAction: (GlassTopAction) -> Unit,
) {
    val content = LocalContentColor.current
    if (topActions.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            topActions.forEach { action ->
                val tint = if (action.destructive) DestructiveColor else content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (action.selected) content.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onTopAction(action) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(painter = painterResource(action.icon), contentDescription = null, tint = tint)
                    Text(text = action.label, fontSize = 12.sp, color = tint, maxLines = 1)
                }
            }
        }
        GlassMenuDivider()
    }
    GlassHighlightGroup(
        count = entries.size,
        selectable = { i -> entries[i].let { it.enabled && !it.isHeader && !it.isDivider } },
    ) { rowModifier ->
        Column(modifier = Modifier.fillMaxWidth()) {
            entries.forEachIndexed { i, entry ->
                when {
                    entry.isDivider -> GlassMenuDivider(modifier = rowModifier(i))
                    entry.isHeader ->
                        Text(
                            text = entry.label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = content.copy(alpha = 0.6f),
                            modifier = rowModifier(i).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        )
                    else -> GlassRow(entry = entry, modifier = rowModifier(i), onClick = { onEntry(entry) })
                }
            }
        }
    }
}

@Composable
private fun GlassRow(
    entry: GlassMenuEntry,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val content = LocalContentColor.current
    val tint = when {
        !entry.enabled -> content.copy(alpha = 0.35f)
        entry.destructive -> DestructiveColor
        else -> content
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(enabled = entry.enabled, onClick = onClick)
                .padding(horizontal = 16.dp),
    ) {
        entry.icon?.let {
            Icon(painter = painterResource(it), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(text = entry.label, fontSize = 16.sp, color = tint, modifier = Modifier.weight(1f))
        entry.shortcut?.let {
            Text(text = it, fontSize = 14.sp, color = content.copy(alpha = 0.6f))
            Spacer(Modifier.width(8.dp))
        }
        entry.selected?.let { selected ->
            Icon(
                painter = painterResource(
                    if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
                ),
                contentDescription = null,
                tint = content,
            )
        }
        if (entry.hasSubmenu) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = content,
                modifier = Modifier.width(18.dp),
            )
        }
    }
}

