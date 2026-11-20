package com.widgetly.app

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private val MatrixLime = Color(0xFFC8F23A)
private val MatrixInk = Color(0xFF0E0F0C)
private val MatrixPanel = Color(0xFF1A1B17)
private val MatrixMuted = Color(0xFF8B8D82)
private const val MATRIX_PREFS = "nexus_matrix_account"

internal data class MatrixRoom(val id: String, val name: String, val lastMessage: String = "")
internal data class MatrixMessage(val id: String, val sender: String, val body: String, val timestamp: Long)

internal object NexusMatrixClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun saved(context: Context): JSONObject {
        val p = context.getSharedPreferences(MATRIX_PREFS, Context.MODE_PRIVATE)
        return JSONObject().put("homeserver", p.getString("homeserver", "https://matrix.org"))
            .put("userId", p.getString("userId", "")).put("token", p.getString("token", ""))
            .put("deviceId", p.getString("deviceId", ""))
    }

    fun save(context: Context, data: JSONObject) {
        context.getSharedPreferences(MATRIX_PREFS, Context.MODE_PRIVATE).edit()
            .putString("homeserver", data.optString("homeserver").trimEnd('/'))
            .putString("userId", data.optString("userId"))
            .putString("token", data.optString("token"))
            .putString("deviceId", data.optString("deviceId")).apply()
    }

    private fun request(base: String, path: String, token: String = "", body: JSONObject? = null, method: String = "GET"): JSONObject {
        val url = base.trimEnd('/') + "/_matrix/client/v3/" + path.trimStart('/')
        val builder = Request.Builder().url(url)
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType))
        else if (method != "GET") builder.method(method, ByteArray(0).toRequestBody(null))
        http.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                throw IllegalStateException(detail.ifBlank { "Matrix returned HTTP ${response.code}" })
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    suspend fun login(context: Context, homeserver: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val base = homeserver.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) { "Enter a homeserver URL beginning with https://." }
        val result = request(base, "login", body = JSONObject()
            .put("type", "m.login.password")
            .put("identifier", JSONObject().put("type", "m.id.user").put("user", username.trim()))
            .put("password", password)
            .put("initial_device_display_name", "Nexus Android"), method = "POST")
        val userId = result.optString("user_id")
        val token = result.optString("access_token")
        if (userId.isBlank() || token.isBlank()) error("The homeserver did not return a valid login session.")
        save(context, JSONObject().put("homeserver", base).put("userId", userId)
            .put("token", token).put("deviceId", result.optString("device_id")))
        userId
    }

    suspend fun rooms(context: Context): List<MatrixRoom> = withContext(Dispatchers.IO) {
        val a = saved(context); val base = a.optString("homeserver"); val token = a.optString("token")
        check(token.isNotBlank()) { "Sign in to your Matrix-compatible account first." }
        val ids = request(base, "joined_rooms", token).optJSONArray("joined_rooms") ?: return@withContext emptyList()
        (0 until ids.length()).mapNotNull { i ->
            val id = ids.optString(i)
            runCatching {
                val state = request(base, "rooms/${Uri.encode(id)}/state/m.room.name", token)
                MatrixRoom(id, state.optString("name").ifBlank { id })
            }.getOrElse { MatrixRoom(id, id) }
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun messages(context: Context, roomId: String): List<MatrixMessage> = withContext(Dispatchers.IO) {
        val a = saved(context); val base = a.optString("homeserver"); val token = a.optString("token")
        val response = request(base, "rooms/${Uri.encode(roomId)}/messages?dir=b&limit=60", token)
        val chunk = response.optJSONArray("chunk") ?: return@withContext emptyList()
        (0 until chunk.length()).mapNotNull { i ->
            val event = chunk.optJSONObject(i) ?: return@mapNotNull null
            if (event.optString("type") == "m.room.encrypted") throw IllegalStateException("This room is end-to-end encrypted. The current Nexus Matrix client does not yet include the encryption SDK, so it cannot decrypt this conversation.")\n            if (event.optString("type") != "m.room.message") return@mapNotNull null
            val content = event.optJSONObject("content") ?: return@mapNotNull null
            val body = content.optString("body")
            if (body.isBlank()) return@mapNotNull null
            MatrixMessage(event.optString("event_id"), event.optString("sender"), body, event.optLong("origin_server_ts"))
        }.reversed()
    }

    suspend fun send(context: Context, roomId: String, text: String) = withContext(Dispatchers.IO) {
        val a = saved(context); val base = a.optString("homeserver"); val token = a.optString("token")
        val txn = UUID.randomUUID().toString()
        request(base, "rooms/${Uri.encode(roomId)}/send/m.room.message/$txn", token,
            JSONObject().put("msgtype", "m.text").put("body", text), "PUT")
    }

    suspend fun createDirect(context: Context, userId: String): String = withContext(Dispatchers.IO) {
        val a = saved(context)
        val result = request(a.optString("homeserver"), "createRoom", a.optString("token"),
            JSONObject().put("is_direct", true).put("preset", "private_chat")
                .put("invite", org.json.JSONArray().put(userId.trim()))
                .put("initial_state", org.json.JSONArray().put(JSONObject()
                    .put("type", "m.room.name").put("state_key", "").put("content", JSONObject().put("name", "Direct message")))), "POST")
        result.optString("room_id").also { if (it.isBlank()) error("The homeserver did not create the room.") }
    }

    suspend fun logout(context: Context) = withContext(Dispatchers.IO) {
        val a = saved(context)
        runCatching { request(a.optString("homeserver"), "logout", a.optString("token"), JSONObject(), "POST") }
        context.getSharedPreferences(MATRIX_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
internal fun NexusMatrixPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = remember { NexusMatrixClient.saved(context) }
    var homeserver by remember { mutableStateOf(account.optString("homeserver", "https://matrix.org")) }
    var username by remember { mutableStateOf(account.optString("userId").substringBefore(':').removePrefix("@")) }
    var password by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf(account.optString("userId")) }
    var signedIn by remember { mutableStateOf(account.optString("token").isNotBlank()) }
    var rooms by remember { mutableStateOf<List<MatrixRoom>>(emptyList()) }
    var selected by remember { mutableStateOf<MatrixRoom?>(null) }
    var messages by remember { mutableStateOf<List<MatrixMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var inviteUser by remember { mutableStateOf("") }
    var showNewDm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refreshRooms() {
        scope.launch {
            busy = true; error = null
            try { rooms = NexusMatrixClient.rooms(context) }
            catch (e: Exception) { error = e.localizedMessage ?: "Could not load rooms." }
            finally { busy = false }
        }
    }
    fun openRoom(room: MatrixRoom) {
        selected = room
        scope.launch {
            busy = true; error = null
            try { messages = NexusMatrixClient.messages(context, room.id) }
            catch (e: Exception) { error = e.localizedMessage ?: "Could not load messages." }
            finally { busy = false }
        }
    }
    LaunchedEffect(signedIn) { if (signedIn) refreshRooms() }

    if (!signedIn) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("NEXUS MESSENGER", color = MatrixLime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Your conversations, inside Nexus.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("Connect a Matrix-compatible account. A homeserver with messaging bridges can also expose supported networks in the same inbox.", color = MatrixMuted, fontSize = 12.sp)
            OutlinedTextField(homeserver, { homeserver = it }, Modifier.fillMaxWidth(), label = { Text("Homeserver URL") }, singleLine = true)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username or localpart") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
            Button(onClick = {
                scope.launch {
                    busy = true; error = null
                    try { userId = NexusMatrixClient.login(context, homeserver, username, password); signedIn = true; password = "" }
                    catch (e: Exception) { error = e.localizedMessage ?: "Login failed." }
                    finally { busy = false }
                }
            }, enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MatrixLime, contentColor = MatrixInk), modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "CONNECTING…" else "SIGN IN")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MatrixLime)
            error?.let { Text(it, color = Color(0xFFFF9A80), fontSize = 12.sp) }
            Text("Nexus speaks the Matrix client protocol directly; it does not launch Beeper. To reach WhatsApp, Telegram or other networks, your Matrix account must have the corresponding bridges configured. Beeper's private infrastructure is not assumed to be a public API.", color = MatrixMuted, fontSize = 10.sp)
        }
        return
    }

    if (selected != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = MatrixLime, fontSize = 22.sp, modifier = Modifier.clickable { selected = null; messages = emptyList() }.padding(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(selected!!.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(selected!!.id, color = MatrixMuted, fontSize = 9.sp, maxLines = 1)
                }
                TextButton(onClick = { openRoom(selected!!) }) { Text("REFRESH", color = MatrixLime, fontSize = 9.sp) }
            }
            if (busy && messages.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MatrixLime) }
            else LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false, verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(messages, key = { it.id }) { msg ->
                    val mine = msg.sender == userId
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Column(Modifier.widthIn(max = 300.dp).background(if (mine) Color(0xFF303A20) else MatrixPanel, RoundedCornerShape(14.dp)).padding(11.dp)) {
                            Text(msg.sender, color = MatrixLime, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(msg.body, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Message…") }, maxLines = 4)
                Button(onClick = {
                    val body = draft.trim()
                    if (body.isNotEmpty()) scope.launch {
                        draft = ""
                        try { NexusMatrixClient.send(context, selected!!.id, body); messages = NexusMatrixClient.messages(context, selected!!.id) }
                        catch (e: Exception) { error = e.localizedMessage ?: "Message could not be sent."; draft = body }
                    }
                }, enabled = draft.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = MatrixLime, contentColor = MatrixInk)) { Text("SEND") }
            }
            error?.let { Text(it, color = Color(0xFFFF9A80), fontSize = 10.sp) }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MESSAGES", color = MatrixLime, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(userId, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { refreshRooms() }, enabled = !busy) { Text("REFRESH", color = MatrixLime) }
                TextButton(onClick = { scope.launch { NexusMatrixClient.logout(context); signedIn = false; rooms = emptyList() } }) { Text("LOG OUT", color = MatrixMuted, fontSize = 9.sp) }
            }
            Button(onClick = { showNewDm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MatrixLime, contentColor = MatrixInk)) { Text("+ NEW DIRECT MESSAGE") }
            error?.let { Text(it, color = Color(0xFFFF9A80), fontSize = 11.sp) }
            if (busy && rooms.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MatrixLime) }
            else if (rooms.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No joined conversations yet. Start a direct message using a Matrix user ID.", color = MatrixMuted, fontSize = 12.sp)
            } else LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(rooms, key = { it.id }) { room ->
                    Row(Modifier.fillMaxWidth().background(MatrixPanel, RoundedCornerShape(12.dp)).clickable { openRoom(room) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Color(0xFF303A20), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(room.name.take(1).uppercase(), color = MatrixLime, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(room.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(room.id, color = MatrixMuted, fontSize = 9.sp, maxLines = 1)
                        }
                        Text("›", color = MatrixLime, fontSize = 22.sp)
                    }
                }
            }
        }
    }
    if (showNewDm) AlertDialog(
        onDismissRequest = { showNewDm = false },
        title = { Text("New direct message") },
        text = { OutlinedTextField(inviteUser, { inviteUser = it }, label = { Text("Matrix user ID (@name:server)") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            scope.launch {
                busy = true; error = null
                try { NexusMatrixClient.createDirect(context, inviteUser); showNewDm = false; inviteUser = ""; refreshRooms() }
                catch (e: Exception) { error = e.localizedMessage ?: "Could not create conversation." }
                finally { busy = false }
            }
        }, enabled = inviteUser.startsWith("@") && inviteUser.contains(":")) { Text("CREATE") } },
        dismissButton = { TextButton(onClick = { showNewDm = false }) { Text("CANCEL") } }
    )
}
