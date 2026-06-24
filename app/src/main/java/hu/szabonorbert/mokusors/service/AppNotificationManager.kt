package hu.szabonorbert.mokusors.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import hu.szabonorbert.mokusors.MainActivity
import hu.szabonorbert.mokusors.R
import java.util.Date

// Listens to Firestore notification collections (like iOS AppNotificationStore)
// and shows local Android notifications for new entries
object AppNotificationManager {

    private var listener: ListenerRegistration? = null
    private val seenIds = mutableSetOf<String>()
    private var startedAt = Date()
    private var isAdmin = false

    fun start(context: Context, isAdmin: Boolean) {
        val collection = if (isAdmin) "appNotifications" else "userAppNotifications"
        if (listener != null && this.isAdmin == isAdmin) return

        this.isAdmin = isAdmin
        stop()
        startedAt = Date()
        seenIds.clear()

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        listener = FirebaseFirestore.getInstance()
            .collection(collection)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    if (!seenIds.add(doc.id)) return@forEach

                    val data = doc.data
                    val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return@forEach
                    if (createdAt.before(Date(startedAt.time - 2000))) return@forEach

                    val title = (data["title"] as? String)?.trim() ?: return@forEach
                    if (title.isEmpty()) return@forEach
                    val body = (data["body"] as? String)?.trim() ?: ""
                    val type = (data["type"] as? String)?.trim() ?: ""
                    val targetUid = (data["targetUid"] as? String)?.trim() ?: ""

                    if (type == "deadline-task-reminder" && !isAdmin) return@forEach
                    // Targeted notifications: only show if targetUid matches or empty
                    if (!isAdmin && targetUid.isNotEmpty() && uid != null && targetUid != uid) return@forEach

                    showNotification(context, title, body)
                }
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
        seenIds.clear()
    }

    private fun showNotification(context: Context, title: String, body: String) {
        val channelId = "mokusors_general"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Mókusörs értesítések", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
