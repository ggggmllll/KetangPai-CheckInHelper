package com.ketangpai.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ketangpai.checkin.rememberLocationSampler

@Composable
internal fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加课堂派账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(email, { email = it }, label = { Text("邮箱或手机号") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("添加后会立即登录课堂派并同步课程。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(email, password) }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun UpdatePasswordDialog(email: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新 $email 的课堂派密码") },
        text = {
            OutlinedTextField(password, { password = it }, label = { Text("新密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onSave(password) }, enabled = password.isNotBlank()) { Text("保存并重新登录") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 GPS 地点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("地点名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(latitude, { location.cancel(); latitude = it }, Modifier.weight(1f), label = { Text("纬度") }, singleLine = true)
                    OutlinedTextField(longitude, { location.cancel(); longitude = it }, Modifier.weight(1f), label = { Text("经度") }, singleLine = true)
                }
                OutlinedButton(onClick = ::sampleLocation, enabled = !location.locating, modifier = Modifier.fillMaxWidth()) {
                    if (location.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.LocationOn, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (location.locating) "正在获取位置…" else "采样当前位置")
                }
                LocationSamplingStatus(location)
                if (location.locating) TextButton(onClick = location::cancel) { Text("取消定位，手动输入") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, latitude, longitude) },
                enabled = !location.locating && name.isNotBlank() && latitude.isNotBlank() && longitude.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
