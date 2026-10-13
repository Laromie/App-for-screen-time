package com.laromie.rostok.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavTab(val title: String, val icon: ImageVector)

/**
 * Компактная нижняя панель (≈60dp вместо стандартных 80dp).
 * Под выбранной иконкой плавно появляется «пилюля», иконка и подпись меняют цвет.
 */
@Composable
fun CompactNavBar(tabs: List<NavTab>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
        ) {
            tabs.forEachIndexed { i, tab ->
                NavItem(tab, selected == i, { onSelect(i) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 52.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "pill",
    )
    val pillColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "pillColor",
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "content",
    )
    Column(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(
            Modifier
                .width(pillWidth)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tab.icon, contentDescription = tab.title, tint = contentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tab.title,
            fontSize = 11.sp,
            maxLines = 1,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
