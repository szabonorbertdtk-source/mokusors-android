package hu.szabonorbert.mokusors.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import hu.szabonorbert.mokusors.MainActivity
import hu.szabonorbert.mokusors.R

class MokusorsFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        saveToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // AppNotificationManager handles notifications via Firestore listener when the app is in
        // the foreground — skip FCM here to avoid showing the same notification twice.
        if (AppNotificationManager.isActive()) return
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: return
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val url = remoteMessage.data["url"]?.takeIf { it.isNotBlank() }
        showNotification(title, body, url)
    }

    private fun saveToken(token: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance()
            .collection("pushTokens")
            .document(user.uid)
            .set(
                mapOf(
                    "token" to token,
                    "email" to (user.email ?: ""),
                    "platform" to "android",
                    "updatedAt" to (System.currentTimeMillis() / 1000.0)
                )
            )
    }

    fun showNotification(title: String, body: String, url: String? = null) {
        val channelId = "mokusors_general"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId, "Mókusörs értesítések", NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)

        val intent = if (!url.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, url.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        fun registerToken(context: Context) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    val user = FirebaseAuth.getInstance().currentUser ?: return@addOnSuccessListener
                    FirebaseFirestore.getInstance()
                        .collection("pushTokens")
                        .document(user.uid)
                        .set(
                            mapOf(
                                "token" to token,
                                "email" to (user.email ?: ""),
                                "platform" to "android",
                                "updatedAt" to (System.currentTimeMillis() / 1000.0)
                            )
                        )
                }
        }
    }
}
