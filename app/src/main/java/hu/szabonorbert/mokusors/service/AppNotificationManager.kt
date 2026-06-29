package hu.szabonorbert.mokusors.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import hu.szabonorbert.mokusors.MainActivity
import hu.szabonorbert.mokusors.R
import java.util.Date

// Mirrors iOS AppNotificationStore: admins listen to both collections, users only to userAppNotifications
object AppNotificationManager {

    private var adminListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private var settingsListener: ListenerRegistration? = null
    private val seenIds = mutableSetOf<String>()
    private var startedAt = Date()
    private var activeIsAdmin = false
    private var notifSettings: Map<String, Any> = emptyMap()

    private val contentTypeToSettingKey = mapOf(
        "dataSheet" to "dataSheets",
        "program" to "programs",
        "resume" to "resumes",
        "offer" to "marketplace",
        "document" to "documents",
        "photo" to "photos",
        "inventory" to "inventory",
        "institutionalEvent" to "institutionalEvents"
    )

    fun start(context: Context, isAdmin: Boolean) {
        if (activeIsAdmin == isAdmin) {
            val alreadyRunning = if (isAdmin) adminListener != null && userListener != null
                                 else userListener != null
            if (alreadyRunning) return
        }

        stop()
        startedAt = Date()
        activeIsAdmin = isAdmin
        seenIds.clear()

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            settingsListener = FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("settings").document("notifications")
                .addSnapshotListener { snap, _ ->
                    notifSettings = snap?.data ?: emptyMap()
                }
        }

        // Always listen to userAppNotifications (same as iOS)
        userListener = makeListener(context, "userAppNotifications", uid, isAdmin)

        // Admins also listen to appNotifications (same as iOS)
        if (isAdmin) {
            adminListener = makeListener(context, "appNotifications", uid, isAdmin)
        }
    }

    fun stop() {
        adminListener?.remove()
        userListener?.remove()
        settingsListener?.remove()
        adminListener = null
        userListener = null
        settingsListener = null
        seenIds.clear()
        notifSettings = emptyMap()
        activeIsAdmin = false
    }

    private fun makeListener(
        context: Context,
        collection: String,
        uid: String?,
        isAdmin: Boolean
    ): ListenerRegistration =
        FirebaseFirestore.getInstance()
            .collection(collection)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    if (change.type != DocumentChange.Type.ADDED) return@forEach
                    val doc = change.document
                    if (!seenIds.add(doc.id)) return@forEach

                    val data = doc.data
                    val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return@forEach
                    if (createdAt.before(Date(startedAt.time - 2000))) return@forEach

                    val title = (data["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    val body = (data["body"] as? String)?.trim() ?: ""
                    val type = (data["type"] as? String)?.trim() ?: ""
                    val contentType = (data["contentType"] as? String)?.trim() ?: ""
                    val targetUid = (data["targetUid"] as? String)?.trim() ?: ""

                    if (type == "deadline-task-reminder" && !isAdmin) return@forEach

                    if (targetUid.isNotEmpty() && uid != null && targetUid != uid) return@forEach

                    val settingKey = notificationSettingKey(type, contentType)
                    if (settingKey != null && notifSettings[settingKey] == false) return@forEach

                    showNotification(context, title, body)
                }
            }

    private fun notificationSettingKey(type: String, contentType: String): String? {
        if (type == "deadline-task-reminder" || type.startsWith("deadline-task")) return "deadlineTasks"
        if (type == "institutional-event-created" || contentType == "institutionalEvent") return "institutionalEvents"
        if (type == "program-created" || contentType == "program") return "programs"
        if (type == "dataSheet-created" || contentType == "dataSheet") return "dataSheets"
        if (type == "resume-created" || contentType == "resume") return "resumes"
        if (type == "offer-created" || contentType == "offer" || type == "marketplace-offer-created") return "marketplace"
        if (type == "inventory_new_item" || type == "inventory_reserved") return "inventory"
        return contentTypeToSettingKey[contentType]
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
