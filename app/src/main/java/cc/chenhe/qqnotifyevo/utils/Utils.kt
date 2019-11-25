package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Android O+ 通知渠道 id
const val NOTIFY_FRIEND_CHANNEL_ID = "QQ_Friend"
const val NOTIFY_GROUP_CHANNEL_ID = "QQ_Group"
const val NOTIFY_QZONE_CHANNEL_ID = "QQ_Zone"

const val GITHUB_URL = "https://github.com/liangchenhe55/QQ-Notify-Evolution"

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
