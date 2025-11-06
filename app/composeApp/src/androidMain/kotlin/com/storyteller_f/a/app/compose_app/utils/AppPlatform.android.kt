package com.storyteller_f.a.app.compose_app.utils

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.storyteller_f.a.app.BuildConfig
import com.storyteller_f.a.app.R
import com.storyteller_f.a.app.compose_app.AppConfig
import com.storyteller_f.a.app.compose_app.BubbleActivity
import com.storyteller_f.a.app.compose_app.MainActivity
import com.storyteller_f.a.app.compose_app.RTCActivity
import com.storyteller_f.a.app.compose_app.common.getDeepLink
import com.storyteller_f.a.app.compose_app.components.mainAppRef
import com.storyteller_f.a.app.compose_app.getClipFile
import com.storyteller_f.a.app.compose_app.getOrCreateNotificationChannel
import com.storyteller_f.a.app.compose_app.initFromContext
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.getAppContextRefValue
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.safeFirstUnicode
import com.strabled.composepreferences.utilis.DataStoreManager
import dev.jordond.connectivity.Connectivity
import okio.Path.Companion.toOkioPath
import org.unifiedpush.android.connector.UnifiedPush
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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

val notifyId = AtomicInteger(0)

actual suspend fun notifyNotification(room: RoomInfo, bitmap: ImageBitmap?) {
    val context = appContextRef.get() ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }
    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        val androidBitmap =
            bitmap?.asAndroidBitmap() ?: getIconBitmapFromName(context, room.name) ?: return
        val iconCompat = IconCompat.createWithBitmap(androidBitmap)
        val channel = "Message"
        val managerCompat = getOrCreateNotificationChannel(context, channel)

        val person = Person.Builder()
            .setName("Chat partner")
            .build()
        val user = Person.Builder().setName("You").build()

        val shortcutId = "room_${room.id}"
        createShortcut(context, shortcutId, person, room, iconCompat)

        val notification = getBubbleNotificationBuilder(
            context,
            user,
            channel,
            shortcutId,
            person,
            room,
            iconCompat
        )
        managerCompat.notify(notifyId.getAndIncrement(), notification.build())
    }
}

private fun getBubbleNotificationBuilder(
    context: Application,
    user: Person,
    channel: String,
    shortcutId: String,
    person: Person,
    room: RoomInfo,
    bitmap: IconCompat,
): NotificationCompat.Builder {
    val bubbleIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, BubbleActivity::class.java)
            .putExtra("roomId", room.id),
        flagUpdateCurrent(true)
    )
    val bubbleData =
        NotificationCompat.BubbleMetadata.Builder(bubbleIntent, bitmap).setDesiredHeight(600)
    val style = NotificationCompat.MessagingStyle(user).setGroupConversation(false)
    return NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notify)
        .setBubbleMetadata(bubbleData.build())
        .setLocusId(LocusIdCompat(shortcutId))
        .setShortcutId(shortcutId)
        .addPerson(person)
        .setStyle(style)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                3,
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW).setData(getDeepLink("/room/${room.id}").toUri()),
                flagUpdateCurrent(true)
            ),
        )
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setContentTitle(room.name)
}

private fun createShortcut(
    context: Application,
    shortcutId: String,
    person: Person,
    room: RoomInfo,
    iconCompat: IconCompat
) {
    val category = "com.storyteller_f.a.category.SHARE_MESSAGE_TARGET"

    val builder = ShortcutInfoCompat.Builder(context, shortcutId)
        .setLocusId(LocusIdCompat(shortcutId))
        .setCategories(setOf(category))
        .setActivity(ComponentName(context, MainActivity::class.java))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(getDeepLink("/room/${room.id}").toUri())
        )
        .setPerson(person)
        .setLongLived(true)
        .setShortLabel(room.name)
        .setIcon(iconCompat)
    ShortcutManagerCompat.pushDynamicShortcut(context, builder.build())
}

actual fun getDeepLinkHost(): String {
    return AppConfig.DEEP_LINK_HOST
}

actual fun getDeepLinkScheme(): String {
    return "${AppConfig.DEEP_LINK_SCHEME_PREFIX}${if (BuildConfig.DEBUG) "-debug" else ""}"
}

suspend fun getIconBitmapFromName(context: Context, name: String): Bitmap? {
    return svgStringToBitmap(
        context,
        """<svg xmlns="http://www.w3.org/2000/svg"
     width="40" height="30" viewBox="0 0 400 300"
     preserveAspectRatio="xMidYMid meet" role="img" aria-label="Centered star">
  <!-- 背景（可删） -->
  <rect x="0" y="0" width="400" height="300" fill="#BCECE7" stroke="#cccccc"/>

  <!-- 居中 Unicode 字符 -->
  <text x="200" y="150"
        text-anchor="middle"
        dominant-baseline="central"
        font-family="system-ui, -apple-system, 'Segoe UI', Roboto, 'Noto Color Emoji', sans-serif"
        font-size="160"
        fill="#1f77b4">
    ${safeFirstUnicode(name)}
  </text>
</svg>
"""
    )
}

suspend fun svgStringToBitmap(
    context: Context,
    svgString: String
): Bitmap? {
    val file = File(context.cacheDir, "temp.svg")
    file.writeText(svgString)
    return SingletonImageLoader.get(context)
        .execute(ImageRequest.Builder(context).data(file).build()).image?.toBitmap()
}

private fun flagUpdateCurrent(mutable: Boolean): Int {
    return if (mutable) {
        if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
