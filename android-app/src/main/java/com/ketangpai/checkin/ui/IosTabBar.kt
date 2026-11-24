package com.ketangpai.checkin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.Page
import com.ketangpai.checkin.iosColors

private val outLinedIcon: Map<Page, ImageVector> = mapOf(
    Page.HOME to Icons.Outlined.Home,
    Page.PENDING to Icons.Outlined.CheckCircle,
    Page.ACCOUNTS to Icons.Outlined.AccountCircle,
    Page.PLACES to Icons.Outlined.LocationOn,
    Page.HISTORY to Icons.Outlined.History,
)

private val filledIcon: Map<Page, ImageVector> = mapOf(
    Page.HOME to Icons.Filled.Home,
    Page.PENDING to Icons.Filled.CheckCircle,
    Page.ACCOUNTS to Icons.Filled.AccountCircle,
    Page.PLACES to Icons.Filled.LocationOn,
    Page.HISTORY to Icons.Filled.History,
)

/**
 * 底部标签栏：49dp 高、图标 24dp、标签 10sp，
 * 未选中用线描图标、选中换实心并染成系统蓝，和 iOS 的 TabBar 行为一致。
 */
@Composable
internal fun IosTabBar(current: Page, onSelect: (Page) -> Unit) {
    Column(Modifier.fillMaxWidth().background(iosColors.card).navigationBarsPadding()) {
        IosDivider(startIndent = 0.dp)
        Row(Modifier.fillMaxWidth().height(49.dp), verticalAlignment = Alignment.CenterVertically) {
            Page.entries.forEach { page ->
                val selected = page == current
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { onSelect(page) }
                        .padding(top = 5.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (selected) filledIcon.getValue(page) else outLinedIcon.getValue(page),
                        contentDescription = page.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) iosColors.blue else iosColors.secondaryLabel,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        page.label,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) iosColors.blue else iosColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 顶部工具栏：只有右侧的操作按钮，标题交给页面里的大标题。 */
@Composable
internal fun IosToolbar(actions: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) { actions() }
}

/** iOS bar button 尺寸的图标按钮。 */
@Composable
internal fun IosBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = iosColors.blue,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 尺寸固定的占位，避免工具栏在加载态和常态之间跳动。 */
@Composable
internal fun IosToolbarSpacer() {
    Box(Modifier.size(44.dp))
}
