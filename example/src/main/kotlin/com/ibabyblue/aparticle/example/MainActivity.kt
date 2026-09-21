package com.ibabyblue.aparticle.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ibabyblue.aparticle.AParticleView
import com.ibabyblue.aparticle.CelebrationConfiguration
import com.ibabyblue.aparticle.AParticle
import com.ibabyblue.aparticle.AtmosphereConfiguration
import com.ibabyblue.aparticle.ParticleColor
import com.ibabyblue.aparticle.ParticleEffect
import com.ibabyblue.aparticle.ParticleShape
import com.ibabyblue.aparticle.RiseAndBurstConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var celebrationDemo by remember { mutableStateOf(true) }
                    if (celebrationDemo) CelebrationDemo(onBack = { celebrationDemo = false })
                    else ParticleDemo(onCelebration = { celebrationDemo = true })
                }
            }
        }
    }
}

@Composable
private fun ParticleDemo(onCelebration: () -> Unit) {
    var burstTrigger by remember { mutableIntStateOf(0) }
    var atmosphereActive by remember { mutableStateOf(true) }
    var shape by remember { mutableStateOf<ParticleShape>(ParticleShape.Heart) }
    val burst = remember(shape) {
        ParticleEffect.riseAndBurst(
            RiseAndBurstConfiguration(
                launchShape = shape,
                burstShape = shape,
            ),
        )
    }
    val atmosphere = remember {
        ParticleEffect.atmosphere(
            AtmosphereConfiguration(
                glowColor = ParticleColor(220f / 255f, 70f / 255f, 105f / 255f, 0.22f),
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF120D1A), Color(0xFF241020), Color(0xFF09080D)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("AParticle", style = MaterialTheme.typography.headlineLarge)
            Button(onClick = onCelebration) { Text("Celebration demo") }
            Text(
                "Compose Canvas · transparent · touch passthrough",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { shape = ParticleShape.Heart }) { Text("Heart") }
                Button(onClick = { shape = ParticleShape.Circle }) { Text("Circle") }
                Button(onClick = { shape = ParticleShape.Star }) { Text("Star") }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { burstTrigger += 1 },
            ) {
                Text("Rise and burst")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Atmosphere", modifier = Modifier.weight(1f))
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                Switch(
                    checked = atmosphereActive,
                    onCheckedChange = { atmosphereActive = it },
                )
            }
            Text(
                "Rapid taps create independent playbacks. Turning atmosphere off and on creates a fresh continuous scene.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AParticle(
            effect = burst,
            trigger = burstTrigger,
            modifier = Modifier.fillMaxSize(),
        )
        AParticle(
            effect = atmosphere,
            trigger = atmosphereActive,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Preview both native adapters on light/dark backgrounds and with optional dimming. */
@Composable
private fun CelebrationDemo(onBack: () -> Unit) {
    var trigger by remember { mutableIntStateOf(0) }
    var light by remember { mutableStateOf(false) }
    var backdrop by remember { mutableStateOf(true) }
    var useView by remember { mutableStateOf(false) }
    val effect = ParticleEffect.celebration(CelebrationConfiguration(if (backdrop) 0.25f else 0f))
    MaterialTheme(colorScheme = if (light) androidx.compose.material3.lightColorScheme()
        else androidx.compose.material3.darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(if (light) Color(0xFFFAF8FC) else Color(0xFF100D1A))) {
            Column(Modifier.align(Alignment.TopStart).padding(horizontal = 24.dp, vertical = 48.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Celebration", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("Meteors · fireworks · twinkling stars", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Light background", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(light, onCheckedChange = { light = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Backdrop 25% (next play)", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(backdrop, onCheckedChange = { backdrop = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Android View adapter", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(useView, onCheckedChange = { useView = it })
                }
                Text("Tap again to restart. With system animations off, twelve stationary stars fade gently.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { trigger += 1 }, modifier = Modifier.fillMaxWidth()) { Text("Play celebration") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Other effects") }
            }
            if (useView) {
                AndroidView(
                    factory = { context -> AParticleView(context).apply { tag = trigger } },
                    modifier = Modifier.matchParentSize(),
                    onRelease = { it.stopAll() },
                    update = { view ->
                        if (view.tag != trigger) {
                            view.tag = trigger
                            view.play(effect)
                        }
                    },
                )
            } else {
                AParticle(effect, trigger, Modifier.matchParentSize())
            }
        }
    }
}
