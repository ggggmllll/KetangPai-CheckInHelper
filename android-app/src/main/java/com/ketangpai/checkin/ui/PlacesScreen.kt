package com.ketangpai.checkin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ketangpai.checkin.GpsPlace
import com.ketangpai.checkin.iosColors

@Composable
internal fun PlacesScreen(places: List<GpsPlace>, onAdd: () -> Unit, onDelete: (String) -> Unit) {
    IosPage("GPS 地点", if (places.isEmpty()) "保存常用位置，签到时一键选择" else "共 ${places.size} 个地点") {
        item {
            IosGroup {
                if (places.isEmpty()) {
                    IosEmpty("还没有保存地点。可以在添加时采样当前位置，也可以手动填经纬度。")
                } else {
                    places.forEachIndexed { index, place ->
                        IosRow(
                            title = place.name,
                            subtitle = "${place.latitude}, ${place.longitude}",
                            showDivider = index < places.lastIndex,
                            trailing = {
                                Text(
                                    "删除",
                                    Modifier.clickable { onDelete(place.id) }.padding(vertical = 4.dp),
                                    fontSize = 15.sp,
                                    color = iosColors.red,
                                )
                            },
                        )
                    }
                }
                Text(
                    "添加地点",
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAdd)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    fontSize = 17.sp,
                    color = iosColors.blue,
                )
            }
        }
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
                Spacer(Modifier.height(0.dp))
                Text(
                    "GPS 签到时会用到这里保存的坐标。课堂派对距离有要求，坐标越接近实际位置越稳。",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = iosColors.secondaryLabel,
                )
            }
        }
    }
}
