package com.storyteller_f.a.app.compose_app.utils

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import com.storyteller_f.a.app.BuildConfig
import com.storyteller_f.a.app.R
import com.storyteller_f.a.app.compose_app.AppConfig
import com.storyteller_f.a.app.compose_app.BubbleActivity
import com.storyteller_f.a.app.compose_app.MainActivity
import com.storyteller_f.a.app.compose_app.RTCActivity
import com.storyteller_f.a.app.compose_app.components.mainAppRef
import com.storyteller_f.a.app.compose_app.getClipFile
import com.storyteller_f.a.app.compose_app.getOrCreateNotificationChannel
import com.storyteller_f.a.app.compose_app.initFromContext
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.getAppContextRefValue
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import okio.Path.Companion.toOkioPath
import org.unifiedpush.android.connector.UnifiedPush

actual val appPlatform: AppPlatform
    get() {
        val activity = mainAppRef?.get()
        val currentState = activity?.lifecycle?.currentState
        val isActive = currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        return AppPlatform(true, isActive, BuildConfig.DEBUG)
    }

actual fun initEnvironment(context: Any) {
    if (context is ComponentActivity) {
        context.initFromContext()
    }
}

actual suspend fun Clipboard.setText(string: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("text", string)))
}

actual fun createConnectivity(): Connectivity {
    return Connectivity {
        autoStart = true
    }
}

actual fun getClientFile(path: String): ClientFile? {
    return getClipFile(getAppContextRefValue()!!, path.toUri())
}

actual fun startCall(roomId: PrimaryKey) {
    val application = getAppContextRefValue() ?: return
    val intent = Intent(application, RTCActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    }
    intent.putExtra("roomId", roomId)
    application.startActivity(intent)
}

private val store by lazy {
    val context = getAppContextRefValue()!!
    DataStoreManager(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                context.filesDir.resolve("main.preferences_pb").toOkioPath()
            }
        )
    )
}

@Composable
actual fun createCustomDataStoreManager(): DataStoreManager {
    return store
}

actual fun unregisterPushService() {
    val context = getAppContextRefValue() ?: return
    UnifiedPush.unregister(context, "A")
}

actual suspend fun notifyNotification(room: RoomInfo) {
    val context = appContextRef.get() ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        val channel = "Message"
        val managerCompat = getOrCreateNotificationChannel(context, channel)
        val target = Intent(context, BubbleActivity::class.java)
            .putExtra("roomId", room.id)
        val bubbleIntent = PendingIntent.getActivity(
            context,
            0,
            target,
            PendingIntent.FLAG_MUTABLE
        )
        val category = "com.storyteller_f.a.category.SHARE_MESSAGE_TARGET"

        val person = Person.Builder()
            .setName("Chat partner")
            .build()
        val user = Person.Builder().setName("You").build()

        // Create a sharing shortcut.
        val shortcutId = "room_${room.id}"
        createShortcut(context, shortcutId, category, person, room)

        val notification = getBubbleNotificationBuilder(
            bubbleIntent,
            context,
            user,
            channel,
            shortcutId,
            person,
            room
        )
        managerCompat.notify(2, notification.build())
    }
}

private fun getBubbleNotificationBuilder(
    bubbleIntent: PendingIntent,
    context: Application,
    user: Person,
    channel: String,
    shortcutId: String,
    person: Person,
    room: RoomInfo
): NotificationCompat.Builder {
    val bubbleData = NotificationCompat.BubbleMetadata.Builder(
        bubbleIntent,
        IconCompat.createWithResource(context, R.drawable.ic_notify)
    ).setDesiredHeight(600).build()
    val style = NotificationCompat.MessagingStyle(user).setGroupConversation(false)
    val notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notify)
        .setBubbleMetadata(bubbleData)
        .setLocusId(LocusIdCompat(shortcutId))
        .setShortcutId(shortcutId)
        .addPerson(person)
        .setStyle(style)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                3,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW),
                PendingIntent.FLAG_MUTABLE,
            ),
        )
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setContentTitle(room.name)
    return notification
}

private fun createShortcut(
    context: Application,
    shortcutId: String,
    category: String,
    person: Person,
    room: RoomInfo
) {
    val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
        .setLocusId(LocusIdCompat(shortcutId))
        .setCategories(setOf(category))
        .setActivity(ComponentName(context, MainActivity::class.java))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
        )
        .setPerson(person)
        .setLongLived(true)
        .setShortLabel(room.name)
        .build()
    ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
}

actual fun getDeepLinkHost(): String {
    return AppConfig.DEEP_LINK_HOST
}

actual fun getDeepLinkScheme(): String {
    return "${AppConfig.DEEP_LINK_SCHEME_PREFIX}${if (BuildConfig.DEBUG) "-debug" else ""}"
}
