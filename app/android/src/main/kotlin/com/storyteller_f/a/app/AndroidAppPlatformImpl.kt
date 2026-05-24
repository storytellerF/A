package com.storyteller_f.a.app

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.storyteller_f.a.app.android_library.R
import com.storyteller_f.a.app.common.getDeepLink
import com.storyteller_f.a.app.utils.AppPlatformImpl
import com.storyteller_f.shared.appContextRef
import com.storyteller_f.shared.model.RoomInfo
import com.storyteller_f.shared.type.PrimaryKey
import com.storyteller_f.shared.utils.safeFirstUnicode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

val notifyId = AtomicInteger(0)

object AndroidAppPlatformImpl : AppPlatformImpl {

    override fun startCall(roomId: PrimaryKey) {
        val application = appContextRef.get() ?: return
        val intent = Intent(application, RTCActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra("roomId", roomId)
        }
        application.startActivity(intent)
    }

    override suspend fun notifyNotification(room: RoomInfo, bitmap: ImageBitmap?) {
        val context = appContextRef.get() ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val androidBitmap = bitmap?.asAndroidBitmap() ?: getIconBitmapFromName(context, room.name) ?: return
            val iconCompat = IconCompat.createWithBitmap(androidBitmap)
            val channel = "Message"
            val managerCompat = getOrCreateNotificationChannel(context, channel)

            val person = Person.Builder()
                .setName("Chat partner")
                .build()
            val user = Person.Builder().setName("You").build()

            val shortcutId = "room_${room.id}"
            createShortcut(context, shortcutId, person, room, iconCompat)

            val notification =
                getBubbleNotificationBuilder(context, user, channel, shortcutId, person, room, iconCompat)
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
            Intent(context, BubbleActivity::class.java).putExtra("roomId", room.id),
            flagUpdateCurrent(true)
        )
        val bubbleData = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, bitmap).setDesiredHeight(600)
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
                        .setAction(Intent.ACTION_VIEW)
                        .setData(getDeepLink("/room/${room.id}").toUri()),
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

    private suspend fun getIconBitmapFromName(context: Application, name: String): Bitmap? {
        return svgStringToBitmap(
            context,
            """<svg xmlns="http://www.w3.org/2000/svg"
     width="40" height="30" viewBox="0 0 400 300"
     preserveAspectRatio="xMidYMid meet" role="img" aria-label="Centered star">
  <rect x="0" y="0" width="400" height="300" fill="#BCECE7" stroke="#cccccc"/>
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

    private suspend fun svgStringToBitmap(
        context: Application,
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
}
