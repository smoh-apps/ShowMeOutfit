package com.smoh.showmeoutfit.ui.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import com.smoh.showmeoutfit.ui.home.previewTodayOutfit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Verifies the real `NotificationManager` output, not just the builder: the collapsed line, the
 * expanded body, the channel importance, and the honest `false` when the channel is blocked.
 */
@RunWith(AndroidJUnit4::class)
class AndroidOutfitNotifierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidOutfitNotifier(context)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        notificationManager.cancelAll()
        // Start from a clean channel so importance assertions are about our own configuration.
        notificationManager.deleteNotificationChannel(AndroidOutfitNotifier.CHANNEL_ID)
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun the_alert_carries_layers_highlights_and_an_app_intent() = runBlocking {
        val outfit = previewTodayOutfit()

        assertTrue(notifier.notifyMorningOutfit(outfit))

        val posted = postedNotification()
        val title = posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val body = posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        val collapsed = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

        assertEquals(AndroidOutfitNotifier.CHANNEL_ID, posted.channelId)
        assertTrue("title was '$title'", title.contains("Morning outfit"))
        assertTrue("title was '$title'", title.contains("22"))

        // Every layer line is present, including the fit-specific garment name.
        assertTrue(body.contains(context.getString(R.string.notification_layer_base)))
        assertTrue(
            body.contains(
                context.getString(
                    GarmentTextMapper.garmentNameRes(GarmentId.COTTON_TEE, GarmentFit.UNISEX)
                )
            )
        )

        // The rain window highlight explains the umbrella that the accessory line lists.
        val firstHighlight = NotificationHighlights.of(outfit.recommendation).first()
        val expectedHighlight = context.getString(
            firstHighlight.resId,
            *firstHighlight.resolve(Locale.getDefault())
        )
        assertTrue("body was '$body'", body.contains(expectedHighlight))
        assertEquals(expectedHighlight, collapsed)

        assertNotNull(posted.contentIntent)
        assertEquals(context.packageName, posted.contentIntent.creatorPackage)
    }

    @Test
    fun the_channel_is_high_importance() {
        runBlocking { notifier.notifyMorningOutfit(previewTodayOutfit()) }

        val channel = notificationManager.getNotificationChannel(AndroidOutfitNotifier.CHANNEL_ID)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun a_blocked_channel_reports_the_alert_as_not_delivered() = runBlocking {
        // Simulate the user blocking only the channel and leaving the app toggle on.
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidOutfitNotifier.CHANNEL_ID,
                "blocked for test",
                NotificationManager.IMPORTANCE_NONE
            )
        )

        assertFalse(notifier.notifyMorningOutfit(previewTodayOutfit()))
    }

    private fun postedNotification(): Notification {
        val record = notificationManager.activeNotifications
            .firstOrNull { it.id == AndroidOutfitNotifier.NOTIFICATION_ID }
            ?: throw AssertionError("notification was not posted")
        return record.notification
    }
}
