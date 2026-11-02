package com.smoh.showmeoutfit.ui.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.smoh.showmeoutfit.MainActivity
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.notification.OutfitNotifier
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import java.text.NumberFormat

/**
 * Posts the morning alert with plain `NotificationCompat` copy.
 *
 * SMO-S3-03 keeps the payload honest but small: the headline temperature range, one line per
 * layer, and the accessory list. SMO-S3-04 owns the rich expansion (rain/UV highlights, deep
 * links) and the permission education flow; this class only has to be safe to call from a
 * background worker and to report when the OS refused the notification.
 */
class AndroidOutfitNotifier(
    private val context: Context
) : OutfitNotifier {

    override suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean {
        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false

        ensureChannel()

        return try {
            notificationManager.notify(NOTIFICATION_ID, build(outfit))
            true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check above and the post.
            false
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private fun build(outfit: TodayOutfit): android.app.Notification {
        val recommendation = outfit.recommendation
        val lineSeparator = System.lineSeparator()
        val layerLines = buildList {
            add(layerLine(R.string.notification_layer_base, recommendation.baseLayer))
            recommendation.midLayer?.let { add(layerLine(R.string.notification_layer_mid, it)) }
            recommendation.outerLayer?.let { add(layerLine(R.string.notification_layer_outer, it)) }
            add(layerLine(R.string.notification_layer_bottom, recommendation.bottomLayer))
            add(layerLine(R.string.notification_layer_footwear, recommendation.footwear))
        }

        val accessories = recommendation.accessories
            .map { context.getString(GarmentTextMapper.accessoryNameRes(it.type)) }
        val accessoryLine = if (accessories.isEmpty()) {
            context.getString(R.string.notification_no_accessories)
        } else {
            context.getString(R.string.notification_accessories, accessories.joinToString(", "))
        }

        val body = (layerLines + accessoryLine).joinToString(lineSeparator)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outfit)
            .setContentTitle(
                context.getString(
                    R.string.notification_title,
                    formatTemperature(recommendation.summary.tempMinCelsius),
                    formatTemperature(recommendation.summary.tempMaxCelsius)
                )
            )
            .setContentText(accessoryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(outfit.weather.snapshot.location.cityName.takeIf { it.isNotBlank() })
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun layerLine(labelRes: Int, garment: Garment): String = context.getString(
        R.string.notification_layer_line,
        context.getString(labelRes),
        context.getString(GarmentTextMapper.garmentNameRes(garment.id, garment.fit))
    )

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTemperature(celsius: Double): String =
        NumberFormat.getIntegerInstance().format(Math.round(celsius))

    companion object {
        const val CHANNEL_ID = "morning_outfit_channel"
        const val NOTIFICATION_ID = 4_101
        private const val REQUEST_CODE = 4_102
    }
}
