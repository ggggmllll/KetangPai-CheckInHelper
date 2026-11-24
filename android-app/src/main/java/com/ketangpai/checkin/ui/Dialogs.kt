package com.ketangpai.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ketangpai.checkin.iosColors
import com.ketangpai.checkin.rememberLocationSampler

@Composable
internal fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    IosSheet(
        title = "添加账号",
        onDismiss = onDismiss,
        confirmText = "添加",
        confirmEnabled = email.isNotBlank() && password.isNotBlank(),
        onConfirm = { onSave(email, password) },
    ) {
        Text(
            "添加后会立即登录课堂派并同步课程。密码用系统密钥加密后只保存在本机。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iosColors.secondaryLabel,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        IosTextField(
            email,
            { email = it },
            "邮箱或手机号",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        IosTextField(
            password,
            { password = it },
            "密码",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun UpdatePasswordDialog(email: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    IosSheet(
        title = "更新密码",
        onDismiss = onDismiss,
        confirmText = "保存",
        confirmEnabled = password.isNotBlank(),
        onConfirm = { onSave(password) },
    ) {
        Text(
            "保存后会重新登录 $email，并刷新课程列表。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iosColors.secondaryLabel,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        IosTextField(
            password,
            { password = it },
            "新密码",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
internal fun AddPlaceDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    val location = rememberLocationSampler {
        latitude = it.latitude.toString()
        longitude = it.longitude.toString()
    }
    val requestLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) location.sample()
        else location.error = "需要定位权限才能采样当前位置"
    }
    fun sampleLocation() {
        location.error = ""
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) location.sample()
        else requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    IosSheet(
        title = "添加地点",
        onDismiss = onDismiss,
        confirmText = "保存",
        confirmEnabled = !location.locating && name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank(),
        onConfirm = { onSave(name, latitude, longitude) },
    ) {
        Text(
            "保存后，GPS 签到时可以直接选这个地点。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = iosColors.secondaryLabel,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        IosTextField(name, { name = it }, "地点名称", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
        Spacer(Modifier.height(10.dp))
        IosTextField(
            latitude,
            { value -> location.cancel(); latitude = value },
            "纬度",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(10.dp))
        IosTextField(
            longitude,
            { value -> location.cancel(); longitude = value },
            "经度",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(14.dp))
        IosButton(
            if (location.locating) "正在获取位置…" else "采样当前位置",
            onClick = ::sampleLocation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !location.locating,
            style = IosButtonStyle.TINTED,
            leading = if (location.locating) {
                { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = iosColors.blue) }
            } else null,
        )
        Column(Modifier.padding(top = 8.dp)) {
            if (location.locating) {
                Text("正在同时获取网络与卫星位置…", fontSize = 13.sp, color = iosColors.secondaryLabel)
            }
            if (location.info.isNotBlank()) {
                Text(location.info, fontSize = 13.sp, lineHeight = 18.sp, color = iosColors.secondaryLabel)
            }
            if (location.error.isNotBlank()) {
                Text(location.error, fontSize = 13.sp, lineHeight = 18.sp, color = iosColors.red)
            }
            if (location.locating) {
                Row(Modifier.padding(top = 6.dp)) {
                    Text(
                        "取消定位",
                        Modifier.clickable(onClick = location::cancel),
                        fontSize = 15.sp,
                        color = iosColors.blue,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}
