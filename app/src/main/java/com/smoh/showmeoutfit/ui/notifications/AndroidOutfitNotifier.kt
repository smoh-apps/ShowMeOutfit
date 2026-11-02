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
import com.smoh.showmeoutfit.ui.AppRoutes
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import java.text.NumberFormat
import java.util.Locale

/**
 * Posts the morning alert: headline range, one line per layer, the accessory list, and the
 * weather highlights that explain the advice.
 *
 * SMO-S3-04 owns the rich expansion (rain/UV/wind highlights, deep link) and the permission
 * education flow; this class only has to be safe to call from a background worker and to report
 * when the OS refused the notification.
 */
class AndroidOutfitNotifier(
    private val context: Context
) : OutfitNotifier {

    override suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean {
        ensureChannel()

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return false
        if (isChannelBlocked()) return false

        return try {
            notificationManager.notify(NOTIFICATION_ID, build(outfit))
            true
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check above and the post.
            false
        }
    }

    /**
     * A user can block the channel while leaving the app's notification toggle on. Posting then
     * succeeds at the API level but nothing is shown, so delivery has to be reported as failed.
     */
    private fun isChannelBlocked(): Boolean {
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
        return channel?.importance == NotificationManager.IMPORTANCE_NONE
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

        // Highlights are only useful when they add a reason the layer list does not already show,
        // so they sit in their own paragraph and stay out of the collapsed single line.
        val highlights = NotificationHighlights.of(recommendation)
            .map { mapped -> context.getString(mapped.resId, *mapped.resolve(Locale.getDefault())) }
        val body = buildList {
            addAll(layerLines)
            add(accessoryLine)
            if (highlights.isNotEmpty()) {
                add("")
                addAll(highlights)
            }
        }.joinToString(lineSeparator)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_outfit)
            .setContentTitle(
                context.getString(
                    R.string.notification_title,
                    formatTemperature(recommendation.summary.tempMinCelsius),
                    formatTemperature(recommendation.summary.tempMaxCelsius)
                )
            )
            .setContentText(highlights.firstOrNull() ?: accessoryLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setSummaryText(accessoryLine)
            )
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

    /**
     * Opens the dashboard.
     *
     * The intent carries the destination the notification is about, but the navigation graph still
     * gates on `isConfigured`: a tap can land on onboarding (when the profile was cleared between
     * delivery and the tap) and must never skip it. Home is the start destination, so a plain
     * activity launch already lands in the right place; the extra keeps the contract explicit for
     * any future deep-link handling.
     */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DESTINATION, AppRoutes.HOME)
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
        const val EXTRA_DESTINATION = "com.smoh.showmeoutfit.extra.DESTINATION"
        private const val REQUEST_CODE = 4_102
    }
}
