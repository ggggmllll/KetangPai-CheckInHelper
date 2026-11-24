package com.ketangpai.checkin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.ketangpai.checkin.BuildConfig
import com.ketangpai.checkin.ThemeMode
import com.ketangpai.checkin.iosColors

private const val REPO_URL = "https://github.com/leidajun666/KetangPai-CheckInHelper"

/**
 * 顶栏设置面板：外观模式 + 扫码后的行为 + 关于。
 * 用底部 sheet 而不是下拉菜单，是因为设置项要带说明文字，
 * 下拉菜单塞不下两行。
 */
@Composable
internal fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    autoSignOnScan: Boolean,
    onAutoSignOnScanChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    IosSheet(
        title = "设置",
        onDismiss = onDismiss,
        confirmText = "完成",
        onConfirm = onDismiss,
    ) {
        ColCaption("外观")
        IosGroup {
            ThemeMode.entries.forEachIndexed { index, mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onThemeModeChange(mode) }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(mode.label, Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
                    if (mode == themeMode) {
                        Icon(Icons.Filled.Check, "已选择", Modifier.size(18.dp), tint = iosColors.blue)
                    }
                }
                if (index < ThemeMode.entries.lastIndex) IosDivider()
            }
        }

        Spacer(Modifier.height(20.dp))

        ColCaption("扫码签到")
        IosGroup {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("扫到二维码后自动签到", Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
                Switch(
                    checked = autoSignOnScan,
                    onCheckedChange = onAutoSignOnScanChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = iosColors.green,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = iosColors.fill.copy(alpha = 0.45f),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        Text(
            if (autoSignOnScan) {
                "扫到码就提交给所有启用账号，结果用顶部提示告知；失败时链接仍会填进待签到页，可以手动重试。"
            } else {
                "只把链接填进待签到页，由你在那里选账号后手动提交。"
            },
            Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iosColors.secondaryLabel,
        )

        Spacer(Modifier.height(20.dp))

        ColCaption("关于")
        AboutBlock()
        Spacer(Modifier.height(18.dp))
    }
}

/**
 * 关于块：应用图标 + 名称 + 版本，下面是一组外链。
 * 图标从 PackageManager 取，自适应图标的 XML 无法用 painterResource 读。
 */
@Composable
private fun AboutBlock() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val icon = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap(144, 144)
                .asImageBitmap()
        }.getOrNull()
    }

    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = "应用图标",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(15.dp)),
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            "课堂派签到助手",
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosColors.label,
        )
        Text(
            "版本 ${BuildConfig.VERSION_NAME}",
            Modifier.padding(top = 2.dp),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = iosColors.secondaryLabel,
        )
        Spacer(Modifier.height(14.dp))
    }

    IosGroup {
        AboutRow("GitHub 仓库", "leidajun666") { open(REPO_URL) }
        IosDivider()
        AboutRow("问题反馈", "Issues") { open("$REPO_URL/issues") }
        IosDivider()
        AboutRow("开源协议", "MIT") { open("$REPO_URL/blob/main/LICENSE") }
    }

    Text(
        "本工具与课堂派官方无关，只在本机调用接口完成签到，账号与密码不会上传到任何第三方。",
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 9.dp),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = iosColors.tertiaryLabel,
        textAlign = TextAlign.Center,
    )
}

/** 关于里的外链行：左侧名称，右侧灰色值 + 细箭头。 */
@Composable
private fun AboutRow(title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
        if (value != null) {
            Text(
                value,
                fontSize = 15.sp,
                color = iosColors.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(5.dp))
        }
        IosChevron()
    }
}

/** sheet 里的分组小标题，比 [IosSectionHeader] 少一层左右边距。 */
@Composable
private fun ColCaption(text: String) {
    Text(
        text,
        Modifier.padding(start = 4.dp, bottom = 6.dp),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = iosColors.secondaryLabel,
    )
}
