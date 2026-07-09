@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package at.seblabs.bahnpendeln

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

private const val PREFS_NAME = "bahnpendeln_prefs"
private const val KEY_STATION_ONE = "station_one"
private const val KEY_STATION_TWO = "station_two"
private const val KEY_ACTIVE_STATION = "active_station"

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
    val actualTime: String,
    val line: String,
    val destination: String,
    val delay: String,
    val delayMinutes: Int,
    val sortMinutes: Int,
)

data class StationSuggestion(
    val id: String,
    val label: String,
    val place: String,
    val anyType: String,
)

sealed interface LiveState {
    data object Idle : LiveState
    data object Loading : LiveState
    data class Ready(val station: String, val stationId: String, val rows: List<Departure>, val loadedAt: String) : LiveState
    data class Error(val message: String) : LiveState
}

sealed interface NearbyState {
    data object Idle : NearbyState
    data object Loading : NearbyState
    data class Ready(val stations: List<NearbyStationResult>) : NearbyState
    data class Error(val message: String) : NearbyState
}

data class NearbyStationResult(
    val label: String,
    val place: String,
    val distanceMeters: Int,
    val stationId: String,
    val departures: List<Departure>,
)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { error ->
        if (cont.isActive) cont.resumeWithException(error)
    }
}

private suspend fun getCurrentLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val client = LocationServices.getFusedLocationProviderClient(context)
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
        .awaitResult()
}

@Composable
private fun BahnpendelnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}

@Composable
private fun BahnpendelnApp() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var stationOne by remember { mutableStateOf(prefs.getString(KEY_STATION_ONE, "") ?: "") }
    var stationTwo by remember { mutableStateOf(prefs.getString(KEY_STATION_TWO, "") ?: "") }
    var activeStation by remember { mutableStateOf(prefs.getInt(KEY_ACTIVE_STATION, 0)) }
    var liveState by remember { mutableStateOf<LiveState>(LiveState.Idle) }
    var nearbyState by remember { mutableStateOf<NearbyState>(NearbyState.Idle) }
    val scope = rememberCoroutineScope()
    val currentStation = if (activeStation == 0) stationOne else stationTwo
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            nearbyState = NearbyState.Loading
            scope.launch {
                nearbyState = runCatching { resolveNearbyStations(context) }
                    .fold(
                        onSuccess = { NearbyState.Ready(it) },
                        onFailure = { error -> NearbyState.Error(error.message ?: "Standortsuche fehlgeschlagen") },
                    )
            }
        } else {
            nearbyState = NearbyState.Error("Standortfreigabe wurde abgelehnt.")
        }
    }
    LaunchedEffect(stationOne) { prefs.edit().putString(KEY_STATION_ONE, stationOne).apply() }
    LaunchedEffect(stationTwo) { prefs.edit().putString(KEY_STATION_TWO, stationTwo).apply() }
    LaunchedEffect(activeStation) { prefs.edit().putInt(KEY_ACTIVE_STATION, activeStation).apply() }

    fun loadLive() {
        if (liveState is LiveState.Loading) return
        val station = currentStation.trim()
        if (station.isBlank()) {
            liveState = LiveState.Error("Bitte zuerst einen Bahnhof eintragen.")
            return
        }
        liveState = LiveState.Loading
        scope.launch {
            liveState = runCatching { resolveStation(station) }
                .mapCatching { resolved ->
                    val rows = fetchDepartures(resolved.id)
                    LiveState.Ready(
                        station = resolved.label,
                        stationId = resolved.id,
                        rows = rows,
                        loadedAt = SimpleDateFormat("HH:mm:ss", Locale.GERMAN).format(Date()),
                    )
                }
                .fold(
                    onSuccess = { it },
                    onFailure = { error -> LiveState.Error(error.message ?: "Live-Abfrage fehlgeschlagen") },
                )
        }
    }

    fun loadNearbyStations() {
        if (nearbyState is NearbyState.Loading) return
        nearbyState = NearbyState.Loading
        scope.launch {
            nearbyState = runCatching {
                resolveNearbyStations(context)
            }.fold(
                onSuccess = { NearbyState.Ready(it) },
                onFailure = { error -> NearbyState.Error(error.message ?: "Standortsuche fehlgeschlagen") },
            )
        }
    }

    fun requestNearbyStations() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            loadNearbyStations()
        } else {
            locationPermissionLauncher.launch(permission)
        }
    }

    fun chooseNearbyStation(label: String) {
        if (activeStation == 0) {
            stationOne = label
        } else {
            stationTwo = label
        }
    }

    LaunchedEffect(Unit) {
        if (currentStation.isNotBlank()) loadLive()
    }

    val scrollState = rememberScrollState()

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
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
                NearbyStationsCard(
                    nearbyState = nearbyState,
                    onRequestNearbyStations = { requestNearbyStations() },
                    onPickStation = { label -> chooseNearbyStation(label) },
                )
                DeparturesCard(
                    station = currentStation,
                    liveState = liveState,
                    onLoadLive = { loadLive() },
                )
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (activeStation == 0) {
                Button(onClick = { onActiveStationChange(0) }, modifier = Modifier.weight(1f)) { Text("Bahnhof 1") }
                OutlinedButton(onClick = { onActiveStationChange(1) }, modifier = Modifier.weight(1f)) { Text("Bahnhof 2") }
            } else {
                OutlinedButton(onClick = { onActiveStationChange(0) }, modifier = Modifier.weight(1f)) { Text("Bahnhof 1") }
                Button(onClick = { onActiveStationChange(1) }, modifier = Modifier.weight(1f)) { Text("Bahnhof 2") }
            }
        }
        Text(
            "1: ${stationOne.ifBlank { "leer" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "2: ${stationTwo.ifBlank { "leer" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditStationCard(
    title: String,
    station: String,
    onStationChange: (String) -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<StationSuggestion>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(station) {
        val query = station.trim()
        if (query.length < 2) {
            suggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(250)
        isSearching = true
        suggestions = runCatching { searchStations(query) }.getOrElse { emptyList() }
        isSearching = false
    }

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
            if (station.trim().length >= 2) {
                Text(
                    if (isSearching && suggestions.isEmpty()) "Suche Vorschläge in der EFA …" else "Vorschläge aus der EFA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(6).forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStationChange(suggestion.label) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(suggestion.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (suggestion.place.isNotBlank() && suggestion.place != suggestion.label) {
                                    Text(suggestion.place, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (suggestions.isEmpty() && !isSearching) {
                        Text(
                            "Keine passenden EFA-Vorschläge gefunden.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyStationsCard(
    nearbyState: NearbyState,
    onRequestNearbyStations: () -> Unit,
    onPickStation: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bahnhaltestellen in der Nähe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Standort nutzen oder manuell suchen — es werden nur Bahnhöfe mit RE/RB angezeigt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestNearbyStations, modifier = Modifier.fillMaxWidth()) {
                Text(if (nearbyState is NearbyState.Loading) "Suche…" else "Bahnhaltestellen in der Nähe")
            }
            when (nearbyState) {
                NearbyState.Idle -> Unit
                NearbyState.Loading -> Text("Standort und Umgebungsbahnhöfe werden geprüft…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is NearbyState.Error -> Text("Fehler: ${nearbyState.message}", color = Color(0xFFB00020))
                is NearbyState.Ready -> {
                    nearbyState.stations.forEach { station ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(station.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (station.place.isNotBlank()) {
                                    Text(station.place, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${station.distanceMeters} m entfernt · RE / RB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                station.departures.firstOrNull()?.let { first ->
                                    Text("Nächste RE/RB: ${first.time} ${first.line} → ${first.destination}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(onClick = { onPickStation(station.label) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Übernehmen")
                                }
                            }
                        }
                    }
                    if (nearbyState.stations.isEmpty()) {
                        Text("Keine RE/RB-Bahnhaltestellen in der Nähe gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeparturesCard(
    station: String,
    liveState: LiveState,
    onLoadLive: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "${station.ifBlank { "Bitte Bahnhof eintragen" }} · RE / RB",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Button(onClick = onLoadLive, enabled = liveState !is LiveState.Loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (liveState is LiveState.Loading) "Lädt…" else "Live laden")
            }
            when (liveState) {
                LiveState.Idle -> Text("Abfahrten erscheinen nach dem Laden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LiveState.Loading -> Text("VRR/EFA wird abgefragt…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is LiveState.Error -> Text("Fehler: ${liveState.message}", color = Color(0xFFB00020))
                is LiveState.Ready -> {
                    val filtered = liveState.rows.filter { row ->
                        val line = row.line.uppercase(Locale.GERMAN)
                        line.startsWith("RE") || line.startsWith("RB")
                    }
                    if (filtered.isEmpty()) {
                        Text("Keine RE/RB-Abfahrten gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        filtered.take(5).forEach { departure ->
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
    val delayLabel = when {
        departure.delayMinutes > 0 -> "+${departure.delayMinutes} → ${departure.actualTime}"
        departure.delayMinutes < 0 -> "${departure.delayMinutes} → ${departure.actualTime}"
        else -> "pünktlich"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(92.dp)) {
            Text(departure.time, fontWeight = FontWeight.Bold)
            Text(delayLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            compactLineLabel(departure.line),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(62.dp),
        )
        Text(
            departure.destination.ifBlank { "Richtung unbekannt" },
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class NearbyRailCandidate(
    val name: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
)

private suspend fun resolveNearbyStations(context: Context): List<NearbyStationResult> = withContext(Dispatchers.IO) {
    val location = getCurrentLocation(context) ?: throw IllegalStateException("Standort konnte nicht ermittelt werden")
    val candidates = queryNearbyRailCandidates(location.latitude, location.longitude)
    val results = mutableListOf<NearbyStationResult>()
    val seenStationIds = mutableSetOf<String>()

    for (candidate in candidates) {
        if (results.size >= 3) break
        val queries = buildList {
            if (candidate.city.isNotBlank()) add("${candidate.city}, ${candidate.name}")
            add(candidate.name)
        }.distinct()

        var resolved: StationSuggestion? = null
        for (query in queries) {
            resolved = try {
                resolveStation(query)
            } catch (_: Exception) {
                null
            }
            if (resolved != null) break
        }
        val station = resolved ?: continue

        if (!seenStationIds.add(station.id)) continue
        val departures = try {
            fetchDepartures(station.id)
        } catch (_: Exception) {
            emptyList()
        }
            .filter {
                val line = it.line.uppercase(Locale.GERMAN)
                line.startsWith("RE") || line.startsWith("RB")
            }
        if (departures.isNotEmpty()) {
            results += NearbyStationResult(
                label = station.label,
                place = station.place,
                distanceMeters = candidate.distanceMeters,
                stationId = station.id,
                departures = departures,
            )
        }
    }

    results.sortedBy { it.distanceMeters }.take(3)
}

private suspend fun queryNearbyRailCandidates(latitude: Double, longitude: Double): List<NearbyRailCandidate> = withContext(Dispatchers.IO) {
    val query = """
        [out:json][timeout:25];
        (
          node(around:2500,$latitude,$longitude)["railway"~"station|halt"];
          way(around:2500,$latitude,$longitude)["railway"~"station|halt"];
          relation(around:2500,$latitude,$longitude)["railway"~"station|halt"];
        );
        out center tags;
    """.trimIndent()

    val connection = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    connection.setRequestProperty("User-Agent", "Bahnpendeln/0.1")
    val postBody = "data=${enc(query)}"
    connection.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
    val bytes = try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.readBytes() ?: return@withContext emptyList()
    } finally {
        connection.disconnect()
    }
    val json = JSONObject(String(bytes, Charsets.UTF_8))
    val elements = json.optJSONArray("elements") ?: return@withContext emptyList()
    buildList {
        for (index in 0 until elements.length()) {
            val element = elements.optJSONObject(index) ?: continue
            val tags = element.optJSONObject("tags") ?: continue
            val name = tags.optString("name").trim()
            if (name.isBlank()) continue
            val city = firstNotBlank(
                tags.optString("addr:city"),
                tags.optString("city"),
                tags.optString("municipality"),
                tags.optString("is_in:city"),
                tags.optString("is_in"),
            )
            val lat = when {
                element.has("lat") -> element.optDouble("lat", Double.NaN)
                element.has("center") -> element.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
                else -> Double.NaN
            }
            val lon = when {
                element.has("lon") -> element.optDouble("lon", Double.NaN)
                element.has("center") -> element.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
                else -> Double.NaN
            }
            if (lat.isNaN() || lon.isNaN()) continue
            add(
                NearbyRailCandidate(
                    name = name,
                    city = city,
                    latitude = lat,
                    longitude = lon,
                    distanceMeters = haversineMeters(latitude, longitude, lat, lon),
                )
            )
        }
    }.sortedBy { it.distanceMeters }
}

private fun firstNotBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return (earthRadius * c).toInt()
}

private suspend fun fetchDepartures(stationId: String): List<Departure> = withContext(Dispatchers.IO) {
    val body = listOf(
        "language" to "de",
        "itdLPxx_contractor" to "ves",
        "mode" to "direct",
        "useAllStops" to "1",
        "includeCompleteStopSeq" to "1",
        "useRealtime" to "1",
        "includedMeans" to "checkbox",
        "inclMOT_0" to "1",
        "name_dm" to stationId,
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
    val html = String(bytes, Charsets.UTF_8)
    parseDepartures(html)
}

private fun parseDepartures(html: String): List<Departure> {
    val rowRegex = Regex(
        """<div[^>]*class="[^"]*std3_departure-line[^"]*"[^>]*data-draw-line="[^"]+"[^>]*>(.*?)(?=<div[^>]*class="[^"]*std3_departure-line[^"]*"[^>]*data-draw-line="[^"]+"|</main>|</body>|$)""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    return rowRegex.findAll(html).mapNotNull { match ->
        val chunk = match.value
        val time = Regex("""<span class="[^"]*std3_time_col[^"]*">(\d{1,2}:\d{2})""")
            .find(chunk)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
        val line = Regex("""data-shortname="([^"]+)""").find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty()
            .ifBlank { Regex("""<span class="[^"]*std3_mot-label[^"]*">.*?</span>\s*([^<]+)</span>""", RegexOption.DOT_MATCHES_ALL).find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty() }
            .ifBlank { "Linie" }
        val direction = Regex("""<div class="std3_result-description"><span class="std3_sr-only">Richtung</span>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
            .find(chunk)?.groupValues?.getOrNull(1)?.plain().orEmpty()
        val delayMinutes = Regex("""data-delay="(-?\d+)""").find(chunk)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val delay = when {
            delayMinutes > 0 -> "+$delayMinutes min"
            delayMinutes < 0 -> "$delayMinutes min"
            else -> "pünktlich"
        }
        val actual = actualDeparture(time, delayMinutes)
        Departure(
            time = time,
            actualTime = actual.time,
            line = line,
            destination = direction,
            delay = delay,
            delayMinutes = delayMinutes,
            sortMinutes = actual.sortMinutes,
        )
    }.sortedBy { it.sortMinutes }.take(20).toList()
}

private data class ActualDeparture(val time: String, val sortMinutes: Int)

private fun actualDeparture(time: String, delayMinutes: Int): ActualDeparture {
    val parts = time.split(":")
    val scheduledMinutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    val actualMinutes = scheduledMinutes + delayMinutes
    val displayMinutes = ((actualMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hour = displayMinutes / 60
    val minute = displayMinutes % 60
    return ActualDeparture("%02d:%02d".format(Locale.GERMAN, hour, minute), actualMinutes)
}

private suspend fun searchStations(query: String): List<StationSuggestion> = withContext(Dispatchers.IO) {
    val url = URL(
        "https://efa.vrr.de/vrr/XSLT_STOPFINDER_REQUEST?" +
            listOf(
                "language" to "de",
                "outputFormat" to "JSON",
                "type_sf" to "any",
                "name_sf" to query,
            ).joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }
    )
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = 12_000
    connection.readTimeout = 12_000
    connection.setRequestProperty("User-Agent", "Bahnpendeln/0.1")
    val bytes = try {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        stream?.readBytes() ?: return@withContext emptyList()
    } finally {
        connection.disconnect()
    }
    val json = JSONObject(String(bytes, Charsets.UTF_8))
    val pointsRaw = json.optJSONObject("stopFinder")?.opt("points") ?: return@withContext emptyList()
    val points = when (pointsRaw) {
        is JSONArray -> pointsRaw
        is JSONObject -> JSONArray().apply {
            val point = pointsRaw.opt("point")
            when (point) {
                is JSONArray -> {
                    for (i in 0 until point.length()) put(point.getJSONObject(i))
                }
                is JSONObject -> put(point)
            }
        }
        else -> return@withContext emptyList()
    }
    val q = normalize(query)
    val candidates = mutableListOf<StationSuggestion>()
    for (i in 0 until points.length()) {
        val point = points.optJSONObject(i) ?: continue
        val label = point.optString("name").trim()
        val place = point.optJSONObject("ref")?.optString("place").orEmpty().trim()
        val id = point.optJSONObject("ref")?.optString("id").orEmpty().trim().ifBlank { point.optString("stateless") }
        val anyType = point.optString("anyType").trim()
        if (!matchesStationQuery(label, place, q)) continue
        candidates += StationSuggestion(id = id, label = label, place = place, anyType = anyType)
    }
    return@withContext candidates
        .distinctBy { normalize(it.label) + "|" + normalize(it.place) + "|" + it.id }
        .sortedWith(compareBy<StationSuggestion> { stationSuggestionScore(it, q) }.thenBy { it.label.length })
        .take(8)
}

private suspend fun resolveStation(query: String): StationSuggestion {
    val candidates = searchStations(query)
    val stopCandidates = candidates.filter { it.anyType == "stop" }
    val picked = stopCandidates.minWithOrNull(
        compareBy<StationSuggestion> { stationSuggestionScore(it, query) }
            .thenBy { it.label.length }
    ) ?: candidates.firstOrNull() ?: throw IllegalStateException("Keine passende Haltestelle gefunden")
    return picked
}

private fun stationSuggestionScore(suggestion: StationSuggestion, query: String): Int {
    val variants = stationQueryVariants(query)
    val label = normalize(suggestion.label)
    val place = normalize(suggestion.place)
    val objectName = normalize(suggestion.label.substringAfter(", ", suggestion.label))
    return when {
        variants.any { label == it || objectName == it } -> 0
        variants.any { label.startsWith("$it,") || label.startsWith("$it ") || label.endsWith(", $it") || label.endsWith(" $it") } -> 1
        variants.any { label.contains(", $it") || label.contains(" $it") || objectName.contains(it) } -> 2
        variants.any { place == it } -> 3
        variants.any { place.startsWith(it) || place.contains(it) } -> 4
        else -> 5
    }
}

private fun matchesStationQuery(label: String, place: String, query: String): Boolean {
    val variants = stationQueryVariants(query)
    val labelN = normalize(label)
    val placeN = normalize(place)
    return variants.any { q ->
        labelN == q || labelN.startsWith("$q,") || labelN.startsWith("$q ") ||
            labelN.endsWith(", $q") || labelN.endsWith(" $q") ||
            labelN.contains(", $q") || labelN.contains(" $q") ||
            placeN == q || placeN.startsWith(q) || placeN.contains(q)
    }
}

private fun stationQueryVariants(query: String): Set<String> {
    val normalized = normalize(query)
    val variants = linkedSetOf(normalized)
    variants += normalized.replace(Regex("""\b([a-z]+)er\b(?=\s+(hbf|bf|bahnhof|hauptbahnhof)\b)"""), "$1")
    if (normalized.contains("hauptbahnhof")) variants += normalized.replace("hauptbahnhof", "hbf")
    if (normalized.contains("bahnhof")) variants += normalized.replace("bahnhof", "bf")
    if (normalized.contains("hbf")) variants += normalized.replace("hbf", "hauptbahnhof")
    return variants.filter { it.isNotBlank() }.toSet()
}

private fun normalize(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase(Locale.GERMAN)
    .trim()

private fun compactLineLabel(line: String): String {
    val cleaned = line.plain()
    val match = Regex("""^([A-ZÄÖÜ]{1,4})\s+(\d+[A-Z]?)\b""").find(cleaned)
    return match?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: cleaned
}

private fun String.plain(): String = replace(Regex("<[^>]+>"), " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace(Regex("""\s+"""), " ")
    .trim()

private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
