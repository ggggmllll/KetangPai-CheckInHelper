package com.ketangpai.checkin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ketangpai.checkin.GpsPlace

@Composable
internal fun PlacesScreen(places: List<GpsPlace>, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GPS 地点列表", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("保存常用位置，GPS 签到时可直接选择。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("添加地点") }
        }
        if (places.isEmpty()) {
            Empty("还没有保存地点。可以手动输入经纬度，或采样当前手机位置。")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(places, key = { it.id }) { place ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(place.name, fontWeight = FontWeight.SemiBold)
                                Text("纬度 ${place.latitude}", style = MaterialTheme.typography.bodySmall)
                                Text("经度 ${place.longitude}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onDelete(place.id) }) {
                                Icon(Icons.Default.DeleteOutline, "删除地点", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
