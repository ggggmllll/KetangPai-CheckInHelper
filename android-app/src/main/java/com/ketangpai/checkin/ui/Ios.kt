package com.ketangpai.checkin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ketangpai.checkin.iosColors
import kotlinx.coroutines.delay

/*
 * 一套最小的 iOS 风格组件。
 *
 * 参照的是 iOS HIG 里最常用的 Inset Grouped 结构：
 * 页面底色浅灰、内容装进 12dp 圆角的白卡、行之间用 0.5dp 内缩分隔线，
 * 而不是每行各自带边框和阴影。这样同样的信息量看起来更安静。
 */

private val GroupCorner = RoundedCornerShape(12.dp)
/** iOS 列表行的最小高度，触摸目标足够大。 */
private val RowMinHeight = 44.dp

/** 页面容器：大标题 + 一串分组。所有内容页都用它，保证边距一致。 */
@Composable
internal fun IosPage(
    title: String,
    caption: String? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            IosLargeTitle(title, caption)
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** 34sp 大标题，iOS 的 large title。 */
@Composable
internal fun IosLargeTitle(title: String, caption: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)) {
        Text(title, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, color = iosColors.label)
        if (!caption.isNullOrBlank()) {
            Text(
                caption,
                Modifier.padding(top = 3.dp),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = iosColors.secondaryLabel,
            )
        }
    }
}

/** 分组上方的小标题，可在右侧带一个蓝色操作入口。 */
@Composable
internal fun IosSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 17.sp, color = iosColors.secondaryLabel)
        if (action != null && onAction != null) {
            Text(
                action,
                Modifier.clickable(onClick = onAction).padding(vertical = 2.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = iosColors.blue,
            )
        }
    }
}

/** 一张分组卡片。卡片内的行自己决定要不要画分隔线。 */
@Composable
internal fun IosGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(GroupCorner).background(iosColors.card),
        content = content,
    )
}

/** 0.5dp 分隔线，默认右侧留 16dp 空白，和 iOS 一致。 */
@Composable
internal fun IosDivider(startIndent: Dp = 16.dp) {
    Box(
        Modifier.fillMaxWidth().padding(start = startIndent).height(0.5.dp).background(iosColors.separator),
    )
}

private val chevronColor: Color
    @Composable get() = iosColors.tertiaryLabel

/** iOS 那种细窄的 › 箭头，比 Material 的 chevron 细一号。 */
@Composable
internal fun IosChevron(modifier: Modifier = Modifier, color: Color = chevronColor) {
    Canvas(modifier.size(width = 8.dp, height = 14.dp)) {
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            moveTo(stroke / 2, stroke / 2)
            lineTo(size.width - stroke / 2, size.height / 2)
            lineTo(stroke / 2, size.height - stroke / 2)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 列表行：可选左侧图标、副标题、右侧值或开关，点击时整行响应。 */
@Composable
internal fun IosRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    showChevron: Boolean = false,
    showDivider: Boolean = true,
    dividerIndent: Dp? = null,
    titleColor: Color = iosColors.label,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = RowMinHeight)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(13.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        Modifier.padding(top = 2.dp),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = iosColors.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.let {
                Spacer(Modifier.width(10.dp))
                it()
            }
            if (showChevron) {
                Spacer(Modifier.width(8.dp))
                IosChevron()
                Spacer(Modifier.width(2.dp))
            }
        }
        if (showDivider) IosDivider(dividerIndent ?: if (leading != null) 60.dp else 16.dp)
    }
}

/** 只读的键值行，用于概览里「待签到 3」这类信息。 */
@Composable
internal fun IosInfoRow(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), fontSize = 17.sp, color = iosColors.label)
            Text(value, fontSize = 17.sp, color = iosColors.secondaryLabel, textAlign = TextAlign.End)
        }
        if (showDivider) IosDivider()
    }
}

internal enum class IosButtonStyle { FILLED, TINTED, PLAIN, DESTRUCTIVE }

/** iOS 的大按钮：50dp 高、14dp 圆角、17sp。 */
@Composable
internal fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: IosButtonStyle = IosButtonStyle.FILLED,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val (background, foreground) = when (style) {
        IosButtonStyle.FILLED -> iosColors.blue to Color.White
        IosButtonStyle.TINTED -> iosColors.fill to iosColors.blue
        IosButtonStyle.PLAIN -> Color.Transparent to iosColors.blue
        IosButtonStyle.DESTRUCTIVE -> iosColors.fill to iosColors.red
    }
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .clip(shape)
            .background(background.copy(alpha = background.alpha * alpha))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (style == IosButtonStyle.PLAIN) 44.dp else 50.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = foreground.copy(alpha = foreground.alpha * alpha),
        )
    }
}

/** 填充式输入框，iOS 的分组表单里就是这种没有边框的浅底圆角框。 */
@Composable
internal fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, fontSize = 17.sp, color = iosColors.secondaryLabel) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        textStyle = TextStyle(fontSize = 17.sp, color = iosColors.label),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = iosColors.fill,
            unfocusedContainerColor = iosColors.fill,
            disabledContainerColor = iosColors.fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = iosColors.blue,
        ),
    )
}

/** 空状态：一句灰字居中，不加卡片和图标。 */
@Composable
internal fun IosEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = iosColors.secondaryLabel,
        textAlign = TextAlign.Center,
    )
}

/**
 * iOS 风格的居中弹窗：270dp 宽、14dp 圆角、标题 17sp 加粗，
 * 按钮横向平分并带一条竖直分隔线。
 */
@Composable
internal fun IosAlert(
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(270.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iosColors.card),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 19.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosColors.label,
                    textAlign = TextAlign.Center,
                )
                if (!message.isNullOrBlank()) {
                    Text(
                        message,
                        Modifier.padding(top = 4.dp),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = iosColors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
                if (content != null) {
                    Spacer(Modifier.height(14.dp))
                    content()
                }
            }
            IosDivider(startIndent = 0.dp)
            Row(Modifier.fillMaxWidth().height(44.dp)) {
                AlertButton(dismissText, iosColors.blue, Modifier.weight(1f), onClick = onDismiss)
                Box(Modifier.width(0.5.dp).height(44.dp).background(iosColors.separator))
                AlertButton(
                    confirmText,
                    if (destructive) iosColors.red else iosColors.blue,
                    Modifier.weight(1f),
                    enabled = confirmEnabled,
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun AlertButton(
    text: String,
    color: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, color = color.copy(alpha = if (enabled) 1f else 0.38f))
    }
}

/** 一行小图标 + 说明，用于分组下方的补充说明。 */
@Composable
internal fun IosFootnote(text: String, color: Color = iosColors.secondaryLabel) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = color,
    )
}

/** 圆形字母头像，替代 Material 的人形图标，视觉上更轻。 */@Composable
internal fun IosAvatar(text: String, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(percent = 50)).background(iosColors.blue.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.take(1).uppercase().ifBlank { "?" },
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.SemiBold,
            color = iosColors.blue,
        )
    }
}

/**
 * 顶部浮层提示，替代 Material 的底部 Snackbar。
 * 深色半透明胶囊 + 白字，2.4 秒后自动消失。
 */
internal class IosToastState {
    internal var message by mutableStateOf<String?>(null)
        private set

    suspend fun show(text: String) {
        message = text
        delay(2400)
        if (message == text) message = null
    }
}

@Composable
internal fun IosToast(state: IosToastState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.message != null,
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Text(
            state.message.orEmpty(),
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6000000))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/** 左侧的小色块，用来区分签到类型，比图标更省空间。 */
@Composable
internal fun IosDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(10.dp)) { drawCircle(color = color, radius = size.minDimension / 2) }
}

/**
 * 底部表单 sheet：取消 / 标题 / 完成 的顶部栏 + 可滚动内容。
 * 表单类操作（添加账号、填地点）用它，确认类操作仍用 [IosAlert]。
 *
 * 这里刻意不用 Dialog：Dialog 是独立的浮动窗口，系统不会让它随输入法缩放，
 * 键盘一弹出整个表单就被顶到屏幕外。放在应用窗口内的覆盖层里，
 * 就能跟着 [WindowInsets.ime] 一起上移，顺带也能盖住底部标签栏。
 */
@Composable
internal fun IosSheet(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 返回键先收表单，而不是直接退出应用。
    BackHandler(onBack = onDismiss)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(120)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(120)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // insets 要压在底色之前，面板底边才会正好停在键盘上方，
                    // 而不是把整块卡片连同键盘一起铺到屏幕底下。
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(iosColors.card)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
            ) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(iosColors.separator)
                        .align(Alignment.CenterHorizontally),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "取消",
                        Modifier.clickable(onClick = onDismiss).padding(vertical = 4.dp),
                        fontSize = 17.sp,
                        color = iosColors.blue,
                    )
                    Text(
                        title,
                        Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosColors.label,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        confirmText,
                        Modifier
                            .then(if (confirmEnabled) Modifier.clickable(onClick = onConfirm) else Modifier)
                            .padding(vertical = 4.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosColors.blue.copy(alpha = if (confirmEnabled) 1f else 0.38f),
                    )
                }
                // 键盘占掉大半屏幕时，表单本身可以滚动，内容不会被裁掉。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}
