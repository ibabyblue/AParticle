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
                    ParticleDemo()
                }
            }
        }
    }
}

@Composable
private fun ParticleDemo() {
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
