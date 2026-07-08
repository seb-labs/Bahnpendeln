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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

data class Departure(
    val time: String,
    val line: String,
    val destination: String,
    val delay: String,
)

sealed interface LiveState {
    data object Idle : LiveState
    data object Loading : LiveState
    data class Ready(val station: String, val rows: List<Departure>, val loadedAt: String) : LiveState
    data class Error(val message: String) : LiveState
}

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
    var liveState by remember { mutableStateOf<LiveState>(LiveState.Idle) }
    val scope = rememberCoroutineScope()
    val lines = listOf("Alle", "RE", "RB", "S", "Bus")
    val currentStation = if (activeStation == 0) stationOne else stationTwo

    fun loadLive() {
        val station = currentStation.trim()
        if (station.isBlank()) {
            liveState = LiveState.Error("Bitte zuerst einen Bahnhof eintragen.")
            return
        }
        liveState = LiveState.Loading
        scope.launch {
            liveState = runCatching { fetchDepartures(station) }
                .fold(
                    onSuccess = { rows ->
                        LiveState.Ready(
                            station = station,
                            rows = rows,
                            loadedAt = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date()),
                        )
                    },
                    onFailure = { error -> LiveState.Error(error.message ?: "Live-Abfrage fehlgeschlagen") },
                )
        }
    }

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
                DeparturesCard(
                    station = currentStation,
                    selectedLine = selectedLine,
                    liveState = liveState,
                    onLoadLive = ::loadLive,
                )
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
            AssistChip(onClick = {}, label = { Text("Live erst per Button") })
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
private fun DeparturesCard(
    station: String,
    selectedLine: String,
    liveState: LiveState,
    onLoadLive: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pendelblick", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(station.ifBlank { "Bitte Bahnhof eintragen" }, fontWeight = FontWeight.Medium)
            Text("Filter: $selectedLine", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onLoadLive, enabled = liveState !is LiveState.Loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (liveState is LiveState.Loading) "Lädt…" else "Live laden")
            }
            when (liveState) {
                LiveState.Idle -> Text("Tippe auf „Live laden“, um Abfahrten für den ausgewählten Bahnhof zu holen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LiveState.Loading -> Text("VRR/EFA wird abgefragt…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is LiveState.Error -> Text("Fehler: ${liveState.message}", color = Color(0xFFB00020))
                is LiveState.Ready -> {
                    val filtered = liveState.rows.filter { row ->
                        selectedLine == "Alle" || row.line.uppercase(Locale.GERMAN).startsWith(selectedLine)
                    }
                    Text("Stand ${liveState.loadedAt} · ${liveState.station}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (filtered.isEmpty()) {
                        Text("Keine passenden Abfahrten gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        filtered.take(8).forEach { departure ->
                            DepartureRow(departure)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: Departure) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(departure.time, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
        Text(departure.line, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(54.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(departure.destination.ifBlank { "Richtung unbekannt" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (departure.delay.isNotBlank()) Text(departure.delay, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                "Die App öffnet ohne Start-Abfrage. Live-Daten werden nur geladen, wenn du den Button drückst.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private suspend fun fetchDepartures(station: String): List<Departure> = withContext(Dispatchers.IO) {
    val body = listOf(
        "language" to "de",
        "itdLPxx_contractor" to "ves",
        "mode" to "direct",
        "useAllStops" to "1",
        "includeCompleteStopSeq" to "1",
        "useRealtime" to "1",
        "name_dm" to station,
        "type_dm" to "stop",
        "nameInfo_dm" to "invalid",
    ).joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    val connection = URL("https://efa.vrr.de/vesstd3/XSLT_DM_REQUEST").openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    connection.setRequestProperty("User-Agent", "Bahnpendeln/0.1")
    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    val bytes = try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.readBytes() ?: throw IllegalStateException("Leere Antwort vom VRR/EFA-Server")
    } finally {
        connection.disconnect()
    }
    val html = String(bytes, Charsets.ISO_8859_1)
    parseDepartures(html)
}

private fun parseDepartures(html: String): List<Departure> {
    val rowStarts = Regex("""std3_departure-line""").findAll(html).map { it.range.first }.toList()
    if (rowStarts.isEmpty()) {
        val times = Regex("""\b\d{1,2}:\d{2}\b""").findAll(html).map { it.value }.distinct().take(8).toList()
        return times.map { Departure(time = it, line = "?", destination = "Abfahrt gefunden", delay = "") }
    }
    return rowStarts.mapIndexedNotNull { index, start ->
        val end = rowStarts.getOrNull(index + 1) ?: html.length
        val chunk = html.substring(start, end)
        val time = Regex("""\b\d{1,2}:\d{2}\b""").find(chunk)?.value ?: return@mapIndexedNotNull null
        val line = Regex("""data-shortname="([^"]+)""").find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty()
            .ifBlank { Regex("""title="([^"]+)""").find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty() }
            .ifBlank { "Linie" }
        val direction = Regex("""Richtung</span>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty()
        val delay = Regex("""data-delay="(-?\d+)""").find(chunk)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            when {
                it > 0 -> "+$it min"
                it < 0 -> "$it min"
                else -> "pünktlich"
            }
        }.orEmpty()
        Departure(time = time, line = line, destination = direction, delay = delay)
    }.take(20)
}

private fun String.plain(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
