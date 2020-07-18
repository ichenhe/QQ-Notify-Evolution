package cc.chenhe.qqnotifyevo.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.os.Environment
import cc.chenhe.qqnotifyevo.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Android O+ 通知渠道 id
const val NOTIFY_FRIEND_CHANNEL_ID = "QQ_Friend"
const val NOTIFY_GROUP_CHANNEL_ID = "QQ_Group"
const val NOTIFY_QZONE_CHANNEL_ID = "QQ_Zone"

const val GITHUB_URL = "https://github.com/liangchenhe55/QQ-Notify-Evolution"

fun getChannelId(channel: NotifyChannel): String = when (channel) {
    NotifyChannel.FRIEND -> NOTIFY_FRIEND_CHANNEL_ID
    NotifyChannel.GROUP -> NOTIFY_GROUP_CHANNEL_ID
    NotifyChannel.QZONE -> NOTIFY_QZONE_CHANNEL_ID
}

/**
 * 创建通知渠道。仅创建渠道实例，未注册到系统。
 */
fun getNotificationChannels(context: Context): List<NotificationChannel> {
    val att = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    val friendChannel = NotificationChannel(NOTIFY_FRIEND_CHANNEL_ID,
            context.getString(R.string.notify_friend_channel_name),
            NotificationManager.IMPORTANCE_HIGH)
    friendChannel.description = context.getString(R.string.notify_friend_channel_des)
    friendChannel.setSound(getRingtone(context, NotifyChannel.FRIEND), att)
    friendChannel.enableVibration(isVibrate(context, NotifyChannel.FRIEND))

    val groupChannel = NotificationChannel(NOTIFY_GROUP_CHANNEL_ID,
            context.getString(R.string.notify_group_channel_name),
            NotificationManager.IMPORTANCE_HIGH)
    groupChannel.description = context.getString(R.string.notify_group_channel_des)
    groupChannel.setSound(getRingtone(context, NotifyChannel.GROUP), att)
    groupChannel.enableVibration(isVibrate(context, NotifyChannel.GROUP))

    val qzoneChannel = NotificationChannel(NOTIFY_QZONE_CHANNEL_ID,
            context.getString(R.string.notify_qzone_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT)
    qzoneChannel.description = context.getString(R.string.notify_qzone_channel_des)
    qzoneChannel.setSound(getRingtone(context, NotifyChannel.QZONE), att)
    qzoneChannel.enableVibration(isVibrate(context, NotifyChannel.QZONE))

    return listOf(friendChannel, groupChannel, qzoneChannel)
}

fun getConversionIcon(context: Context, conversionId: Int): Bitmap? {
    try {
        val file = File(getDiskCacheDir(context, "conversion_icon").absolutePath, conversionId.toString())
        if (file.exists())
            return BitmapFactory.decodeFile(file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

fun saveConversationIcon(context: Context, conversionId: Int, bmp: Bitmap): File {
    val dir = getDiskCacheDir(context, "conversion_icon")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val file = File(dir, conversionId.toString())
    if (!file.exists()) {
        try {
            file.createNewFile()
            val outStream = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.flush()
            outStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
    return file
}

private fun getDiskCacheDir(context: Context, uniqueName: String): File {
    val cachePath: File = if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
            || !Environment.isExternalStorageRemovable()) {
        context.externalCacheDir!!
    } else {
        context.cacheDir
    }
    return File(cachePath, uniqueName)
}
