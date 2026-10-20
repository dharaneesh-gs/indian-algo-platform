package in.trading.platform.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

private val Navy = Color(0xFF0A1220)
private val Panel = Color(0xFF142136)
private val Blue = Color(0xFF5E8FFF)
private val Muted = Color(0xFF9AAAC2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Blue, background = Navy, surface = Panel)) { TradingApp() } }
    }
}

@Composable
private fun TradingApp() {
    var baseUrl by remember { mutableStateOf("http://10.0.2.2:8080") }
    var token by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("Overview") }
    var message by remember { mutableStateOf("Enter the API token from the backend launcher.") }
    var output by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("RELIANCE") }
    var side by remember { mutableStateOf("BUY") }
    var quantity by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("1000") }
    var fast by remember { mutableStateOf("5") }
    var slow by remember { mutableStateOf("20") }
    var startingCash by remember { mutableStateOf("100000") }
    var brokerPin by remember { mutableStateOf("") }
    var brokerTotp by remember { mutableStateOf("") }
    var account by remember { mutableStateOf(JSONObject()) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun call(path: String, method: String = "GET", body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val connection = (URL(cleanBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
            text
        } finally { connection.disconnect() }
    }

    fun action(path: String, method: String = "GET", body: JSONObject? = null, after: (String) -> Unit = { output = it }) {
        scope.launch {
            busy = true
            try { val result = call(path, method, body); output = result; message = "Request completed."; after(result) }
            catch (e: Exception) { message = e.message ?: "Request failed" }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().background(Navy).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("TRADING PLATFORM", color = Blue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(page, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Unbranded · NSE/BSE · Paper-first", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Overview", "Orders", "Strategy", "Backtest", "Broker", "Audit").forEach { label ->
                FilterChip(selected = page == label, onClick = { page = label }, label = { Text(label) })
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backend connection", fontWeight = FontWeight.Bold)
                Field("Backend URL", baseUrl) { baseUrl = it }
                Field("API token", token, secret = true) { token = it }
                Button(onClick = { action("/api/account", after = { raw -> account = JSONObject(raw); message = "Connected to backend." }) }, enabled = !busy && token.length >= 32, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else "Connect / refresh") }
            }
        }
        if (message.isNotBlank()) Text(message, color = if (message.startsWith("HTTP") || message.contains("failed", true) || message.contains("error", true)) Color(0xFFFF9BA7) else Muted)
        when (page) {
            "Overview" -> {
                MetricCard("Portfolio equity", money(account.optDouble("equity", 0.0)))
                MetricCard("Available cash", money(account.optDouble("cash", 0.0)))
                MetricCard("Unrealized P&L", money(account.optDouble("unrealizedPnl", 0.0)))
                Button(onClick = { action("/api/account", after = { account = JSONObject(it) }) }, modifier = Modifier.fillMaxWidth()) { Text("Load paper account") }
                Button(onClick = { action("/api/trades") }, modifier = Modifier.fillMaxWidth()) { Text("View recent trades") }
            }
            "Orders" -> {
                SectionCard("Paper order", "Simulated only; no broker execution") {
                    Field("Symbol", symbol) { symbol = it }
                    Field("Quantity", quantity) { quantity = it }
                    Field("Reference price ₹", price) { price = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = side == "BUY", onClick = { side = "BUY" }, label = { Text("BUY") }); FilterChip(selected = side == "SELL", onClick = { side = "SELL" }, label = { Text("SELL") }) }
                    Button(onClick = { action("/api/paper/orders", "POST", JSONObject().put("symbol", symbol.uppercase()).put("side", side).put("quantity", quantity.toIntOrNull() ?: 1).put("price", price.toDoubleOrNull() ?: 0.0)) }, modifier = Modifier.fillMaxWidth()) { Text("Place paper order") }
                    OutText(output)
                }
                Button(onClick = { action("/api/trades") }, modifier = Modifier.fillMaxWidth()) { Text("Refresh trade ledger") }
            }
            "Strategy" -> SectionCard("SMA crossover", "REST polling; strategy stops when backend restarts") {
                Field("Symbol", symbol) { symbol = it }
                Field("Fast period", fast) { fast = it }
                Field("Slow period", slow) { slow = it }
                Button(onClick = { action("/api/strategy/configure", "POST", JSONObject().put("symbol", symbol.uppercase()).put("exchange", "NSE").put("mode", "PAPER").put("fastPeriod", fast.toIntOrNull() ?: 5).put("slowPeriod", slow.toIntOrNull() ?: 20).put("quantity", quantity.toIntOrNull() ?: 1)) }, modifier = Modifier.fillMaxWidth()) { Text("Save PAPER strategy") }
                Button(onClick = { action("/api/strategy/start", "POST", JSONObject()) }, modifier = Modifier.fillMaxWidth()) { Text("Start strategy") }
                OutText(output)
                OutlinedButton(onClick = { action("/api/strategy/stop", "POST", JSONObject()) }, modifier = Modifier.fillMaxWidth()) { Text("Stop strategy") }
            }
            "Backtest" -> SectionCard("Backtesting", "Historical simulation only") {
                Field("Symbol", symbol) { symbol = it }
                Field("Fast period", fast) { fast = it }
                Field("Slow period", slow) { slow = it }
                Field("Starting cash", startingCash) { startingCash = it }
                Button(onClick = { action("/api/backtest/${symbol.uppercase()}", "POST", JSONObject().put("fastPeriod", fast.toIntOrNull() ?: 5).put("slowPeriod", slow.toIntOrNull() ?: 20).put("startingCash", startingCash.toDoubleOrNull() ?: 100000.0)) }, modifier = Modifier.fillMaxWidth()) { Text("Run backtest") }
                Button(onClick = { action("/api/market/demo", "POST", JSONObject()) }, modifier = Modifier.fillMaxWidth()) { Text("Load synthetic demo candles") }
                OutText(output)
            }
            "Broker" -> SectionCard("Angel One session", "Credentials are not persisted by this screen") {
                Field("PIN", brokerPin, true) { brokerPin = it }
                Field("TOTP", brokerTotp, true) { brokerTotp = it }
                Button(onClick = { action("/api/broker/angel/login", "POST", JSONObject().put("pin", brokerPin).put("totp", brokerTotp), after = { brokerPin = ""; brokerTotp = ""; output = it }) }, modifier = Modifier.fillMaxWidth()) { Text("Login to Angel One") }
                Button(onClick = { action("/api/broker/angel/status") }, modifier = Modifier.fillMaxWidth()) { Text("Broker status") }
                Button(onClick = { action("/api/broker/angel/account") }, modifier = Modifier.fillMaxWidth()) { Text("Account snapshot") }
                OutlinedButton(onClick = { action("/api/broker/angel/logout", "POST", JSONObject()) }, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
                Text("Live orders are intentionally not exposed in this starter Android UI. Use the web dashboard only after server-side live trading is explicitly enabled and verified.", color = Muted)
                OutText(output)
            }
            "Audit" -> { Button(onClick = { action("/api/audit") }, modifier = Modifier.fillMaxWidth()) { Text("Load audit log") }; OutText(output) }
        }
        Text("Security: local development HTTP only. Use HTTPS/VPN for trusted remote access. Live trading stays disabled by default.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun Field(label: String, value: String, secret: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth())
}
@Composable private fun MetricCard(label: String, value: String) { Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text(label, color = Muted); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) } } }
@Composable private fun SectionCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall); content() }) } }
@Composable private fun OutText(value: String) { if (value.isNotBlank()) Text(value.take(5000), color = Color(0xFFBDD0EB), style = MaterialTheme.typography.bodySmall) }
private fun money(value: Double): String = "₹" + String.format(java.util.Locale("en", "IN"), "%,.2f", value)
