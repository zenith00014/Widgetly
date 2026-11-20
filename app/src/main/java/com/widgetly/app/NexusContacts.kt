package com.widgetly.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private data class NexusContact(val id: String, val name: String, val phone: String)

private object NexusContactStore {
    private const val KEY = "nexus_personal_contacts"
    fun load(context: Context): List<NexusContact> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val phone = item.optString("phone")
            if (phone.isBlank()) null else NexusContact(item.optString("id"), item.optString("name"), phone)
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, contacts: List<NexusContact>) {
        val array = JSONArray()
        contacts.distinctBy { it.phone }.take(150).forEach { array.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

@Composable
fun NexusContactsPanel() {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(NexusContactStore.load(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            val selected = runCatching {
                context.contentResolver.query(uri, arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) NexusContact(
                        cursor.getString(0) ?: uri.toString(),
                        cursor.getString(1) ?: "Contact",
                        cursor.getString(2) ?: ""
                    ) else null
                }
            }.getOrNull()
            if (selected != null && selected.phone.isNotBlank()) {
                contacts = (contacts + selected).distinctBy { it.phone }
                NexusContactStore.save(context, contacts)
                error = null
            } else error = "Could not read that phone number from the system picker."
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("PERSONAL CONTACTS", color = Color(0xFFC8F23A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Only contacts you choose are saved.", color = Color(0xFF8B8D82), fontSize = 11.sp)
            }
            Button(onClick = { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8F23A), contentColor = Color(0xFF0E0F0C))) {
                Text("＋ ADD", fontSize = 10.sp)
            }
        }
        Text("Call opens the phone dialler. Message opens your SMS app; Nexus does not send messages itself.", color = Color(0xFF8B8D82), fontSize = 11.sp)
        if (error != null) Text(error!!, color = Color(0xFFFF9A80), fontSize = 11.sp)
        if (contacts.isEmpty()) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NO CONTACT SHORTCUTS", color = Color(0xFFECE9DF), fontWeight = FontWeight.Bold)
                Text("Add a person through Android's contact picker. Nexus does not request broad contact-book access.", color = Color(0xFF8B8D82), fontSize = 12.sp)
            }
        } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(contacts, key = { it.id + it.phone }) { contact ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF1A1B17)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(contact.name, color = Color(0xFFECE9DF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(contact.phone, color = Color(0xFF8B8D82), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", contact.phone, null))) }) { Text("CALL", color = Color(0xFFC8F23A), fontSize = 10.sp) }
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", contact.phone, null))) }) { Text("MESSAGE", color = Color(0xFFC8F23A), fontSize = 10.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { contacts = contacts.filterNot { it.phone == contact.phone }; NexusContactStore.save(context, contacts) }) { Text("REMOVE", color = Color(0xFF8B8D82), fontSize = 9.sp) }
                    }
                }
            }
        }
    }
}
