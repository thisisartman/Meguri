package io.github.thisisartman.collectibles.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R

/**
 * Where the phone points, in degrees from north, from the rotation-vector sensor; null without one (then no arrow
 * is drawn: an arrow relative to north would point the wrong way). Updates are smoothed and limited to about 10 a second.
 */
@Composable
fun rememberHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) return@DisposableEffect onDispose { }
        val r = FloatArray(9); val o = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                SensorManager.getOrientation(r, o)
                val deg = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                val prev = heading
                // Ignore jitter under 2 degrees, so the arrow does not tremble
                if (prev == null || angleDiff(prev, deg) > 2f) heading = deg
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, 100_000)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

private fun angleDiff(a: Float, b: Float): Float = (((a - b) % 360 + 540) % 360 - 180).let { kotlin.math.abs(it) }

/** Initial bearing in degrees from north, from one point to another. */
fun bearing(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Float {
    val out = FloatArray(2)
    Location.distanceBetween(fromLat, fromLng, toLat, toLng, out)
    return (out[1] + 360) % 360
}

/**
 * Distance with an arrow pointing at the place, relative to where the phone points (owner and Organic Maps,
 * 2026-11-07). Without a compass, or without both positions, only the distance.
 */
@Composable
fun DistanceArrow(distance: String, here: Pair<Double, Double>?, there: Pair<Double, Double>?, style: TextStyle = MaterialTheme.typography.labelLarge) {
    val heading = rememberHeading()
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (heading != null && here != null && there != null) {
            val turn = bearing(here.first, here.second, there.first, there.second) - heading
            Icon(
                painterResource(R.drawable.ic_bearing), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).rotate(turn),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(distance, style = style.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary)
    }
}
