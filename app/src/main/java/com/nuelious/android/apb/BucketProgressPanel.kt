package com.nuelious.android.apb

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

@Composable
internal fun BucketProgressPanel(percent:Int,title:String,currentFile:String,details:String,downloaded:Long,total:Long,kind:String,onInfo:(()->Unit)?=null) {
 val fill by animateFloatAsState(percent.coerceIn(0,100)/100f,tween(420),label="bucket-fill")
 val color=when(kind){"delete"->MaterialTheme.colorScheme.error;"move"->MaterialTheme.colorScheme.tertiary;"extract"->MaterialTheme.colorScheme.secondary;else->MaterialTheme.colorScheme.primary}
 Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
  Canvas(Modifier.size(72.dp,92.dp)) { val w=size.width;val h=size.height;val bucket=Path().apply{moveTo(w*.12f,h*.08f);lineTo(w*.88f,h*.08f);lineTo(w*.76f,h*.94f);lineTo(w*.24f,h*.94f);close()};drawPath(bucket,color.copy(alpha=.2f),style=Stroke(4.dp.toPx()));clipPath(bucket){val top=h*(1f-fill);drawRect(color,topLeft=Offset(0f,top),size=androidx.compose.ui.geometry.Size(w,h-top))} }
  Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text("$percent%",style=MaterialTheme.typography.headlineSmall,color=color);Text(title,style=MaterialTheme.typography.titleSmall);if(currentFile.isNotBlank())Text(currentFile,style=MaterialTheme.typography.bodySmall,maxLines=2);if(total>0)Text("$downloaded / $total bytes",style=MaterialTheme.typography.labelSmall);onInfo?.let{TextButton(onClick=it){Text("ⓘ More information")}}}
 } }
}
