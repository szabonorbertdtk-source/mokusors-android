package hu.szabonorbert.mokusors.ui.photos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PhotoFolder(
    val id: String,
    val title: String,
    val href: String
)

private val fallbackFolders = listOf(
    PhotoFolder("1", "Mókusőrs fényképek",
        "https://drive.google.com/drive/folders/16GPtICfk7PxDZi88e4l4_eOeClZPzMWT?usp=sharing"),
    PhotoFolder("2", "Eseményfotók",
        "https://drive.google.com/drive/folders/1sJnRCXmzLUg9a4zeAxCziZrLXbKvk7fG?usp=sharing"),
    PhotoFolder("3", "Megosztott galéria",
        "https://drive.google.com/drive/folders/1NdKN6k6kjTfh4LeOfH9dmkEZG3HNdftN?usp=sharing")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(onBack: () -> Unit) {
    var folders by remember { mutableStateOf(fallbackFolders) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val teal = Color(0xFF30B0C7)

    LaunchedEffect(Unit) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                val url = java.net.URL("https://mokusors-admin.vercel.app/api/drive-folders")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(text)
                val arr = json.optJSONArray("folders")
                if (arr != null && arr.length() > 0) {
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        PhotoFolder(
                            id = obj.optString("id", "$i"),
                            title = obj.optString("name", obj.optString("title", "Névtelen mappa")),
                            href = obj.optString("webViewLink", obj.optString("href", ""))
                        )
                    }
                } else null
            }
            if (loaded != null) folders = loaded
        } catch (_: Exception) {
            // fallback folders remain
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Média", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(folders) { folder ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(teal.copy(alpha = 0.08f))
                        .clickable(enabled = folder.href.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(folder.href)))
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(teal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Photo, null, tint = teal, modifier = Modifier.size(22.dp))
                        }
                        Text(
                            folder.title.ifEmpty { "Névtelen mappa" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (folder.href.isNotBlank()) {
                            Icon(Icons.Default.OpenInNew, null, tint = teal, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
