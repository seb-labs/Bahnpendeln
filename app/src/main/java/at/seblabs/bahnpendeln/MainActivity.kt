@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.seblabs.bahnpendeln

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BahnpendelnTheme { BahnpendelnApp() } }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F7AFF),
    secondary = Color(0xFF00B894),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFFF5F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFE7ECF5),
    onSurface = Color(0xFF152033),
    onSurfaceVariant = Color(0xFF5C667A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7EA8FF),
    secondary = Color(0xFF4CE0B5),
    tertiary = Color(0xFFFFC857),
    background = Color(0xFF0C111B),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF202A3A),
    onSurface = Color(0xFFE7ECF6),
    onSurfaceVariant = Color(0xFFB1BCD1),
)

@Composable
private fun BahnpendelnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

@Composable
private fun BahnpendelnApp() {
    var stationOne by remember { mutableStateOf("Münster Hauptbahnhof") }
    var stationTwo by remember { mutableStateOf("Münster Zentrum Nord") }
    var activeStation by remember { mutableStateOf(0) }
    var selectedLine by remember { mutableStateOf("Alle") }
    val lines = listOf("Alle", "RE", "RB", "S", "Bus")
    val currentStation = if (activeStation == 0) stationOne else stationTwo

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderCard()
                StationSwitch(
                    activeStation = activeStation,
                    onActiveStationChange = { activeStation = it },
                    stationOne = stationOne,
                    stationTwo = stationTwo,
                )
                EditStationCard(
                    title = if (activeStation == 0) "Bahnhof 1" else "Bahnhof 2",
                    station = currentStation,
                    onStationChange = {
                        if (activeStation == 0) stationOne = it else stationTwo = it
                    },
                )
                LineFilterCard(lines, selectedLine) { selectedLine = it }
                DeparturesCard(currentStation, selectedLine)
                InfoCard()
            }
        }
    }
}

@Composable
private fun HeaderCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bahnpendeln", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Zwei Bahnhöfe, klare Linienfilter und ein schneller Pendelblick.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AssistChip(onClick = {}, label = { Text("Frischer stabiler Neustart") })
        }
    }
}

@Composable
private fun StationSwitch(
    activeStation: Int,
    onActiveStationChange: (Int) -> Unit,
    stationOne: String,
    stationTwo: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onActiveStationChange(0) },
            modifier = Modifier.weight(1f),
            enabled = activeStation != 0,
        ) { Text("Bahnhof 1") }
        Button(
            onClick = { onActiveStationChange(1) },
            modifier = Modifier.weight(1f),
            enabled = activeStation != 1,
        ) { Text("Bahnhof 2") }
    }
    Text(
        "Aktuell: ${if (activeStation == 0) stationOne else stationTwo}",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EditStationCard(title: String, station: String, onStationChange: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = station,
                onValueChange = onStationChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Bahnhof") },
            )
        }
    }
}

@Composable
private fun LineFilterCard(lines: List<String>, selectedLine: String, onSelect: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Linienfilter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                lines.forEach { line ->
                    FilterChip(
                        selected = selectedLine == line,
                        onClick = { onSelect(line) },
                        label = { Text(line) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeparturesCard(station: String, selectedLine: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pendelblick", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(station.ifBlank { "Bitte Bahnhof eintragen" }, fontWeight = FontWeight.Medium)
            Text(
                "Filter: $selectedLine · Live-Daten werden im nächsten Schritt sicher ergänzt. Diese Version öffnet stabil ohne Start-Abfrage.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf("Nächste Abfahrten", "Verspätungen", "Gleis/Steig").forEach { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hinweis", fontWeight = FontWeight.SemiBold)
            Text(
                "Diese frische Basis enthält keine alten Projektfragmente und keine privaten Daten. Der Live-Abruf wird danach kontrolliert eingebaut.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
