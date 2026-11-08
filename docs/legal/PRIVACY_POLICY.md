# Privacy Policy for ShowMeOutfit

**Effective Date:** November 8, 2026  
**Application:** ShowMeOutfit (Native Android Application)  
**Developer / Publisher:** smoh-apps  
**Hosting URL:** https://smoh-apps.github.io/ShowMeOutfit/privacy.html  

---

## 1. Introduction & Core Philosophy

**ShowMeOutfit** is built on a zero-tracking, privacy-first, standalone architecture. We believe that your daily outfit advice and local weather conditions should remain strictly personal. 

* **No User Accounts:** You do not need to create an account, register, or provide an email address, name, or phone number to use ShowMeOutfit.
* **No Advertisements or Trackers:** The application contains zero advertising SDKs, zero analytics trackers, and zero behavioral telemetry tools.
* **Zero Custom Backend:** The app operates completely standalone on your device without routing through proprietary intermediary proxy servers.

---

## 2. Information We Collect and How We Use It

### 2.1 Location Data (Foreground Only)
* **What We Access:** When you grant location permissions (`ACCESS_COARSE_LOCATION` or `ACCESS_FINE_LOCATION`), the app retrieves your device's geographical coordinates (latitude and longitude).
* **How It Is Used:** Coordinates are transmitted over an encrypted HTTPS connection directly to the public [Open-Meteo API](https://open-meteo.com/) solely to fetch current and hourly weather forecasts for your vicinity.
* **No Background Location Tracking:** ShowMeOutfit **never** requests or uses `ACCESS_BACKGROUND_LOCATION`. Location fixes are only taken while the application is active in the foreground or when you perform a manual city search.
* **Manual Override:** You may choose not to grant location permissions and instead manually search for any city worldwide. In this case, device GPS is never accessed.

### 2.2 Local Device Storage (Preferences & Caching)
* **What We Store Locally:**
  - **User Preferences:** Preferred clothing fit variant (Unisex, Men, Women), thermal sensitivity comfort offset ($\pm 2.5^\circ\text{C}$), age comfort band, and scheduled morning alert time (e.g., 07:00 AM).
  - **Weather Cache:** The most recent weather forecast snapshot is saved in an on-device SQLite database (Room) for up to 12 hours to enable instant offline access and battery conservation.
* **Data Confinement:** All cached data and preferences remain strictly confined to your device's private app sandbox (`DataStore` and SQLite). This data is never uploaded, synced, or transmitted to any server.

### 2.3 Notifications and Alarms
* **Morning Notification Alert:** If you enable the morning alert, the app uses Android's `AlarmManager` (`SCHEDULE_EXACT_ALARM`) and `WorkManager` locally on your device to wake the processor at your designated morning time to prepare and post the day's outfit notification.
* **No Remote Push:** No push notification servers (e.g. Firebase Cloud Messaging) are utilized. The alert is generated 100% locally on the device.

---

## 3. Third-Party Services

The only external network service the app communicates with is:
* **Open-Meteo REST API (Weather & Geocoding):** Direct client HTTPS queries to `api.open-meteo.com` and `geocoding-api.open-meteo.com`. Open-Meteo is an open-access, keyless meteorological service that does not log personal identifying information. For details, refer to the [Open-Meteo Privacy Policy](https://open-meteo.com/en/terms).

---

## 4. Data Retention and Deletion

Because ShowMeOutfit does not maintain a server or database of user accounts:
* **Instant Deletion:** You can delete all locally stored data, preferences, and cached forecasts at any time by going to your Android device's **Settings > Apps > ShowMeOutfit > Storage > Clear Data**, or by uninstalling the app.
* **Automatic Cache Expiration:** Cached weather rows older than 7 days are automatically pruned from the local database on each refresh.

---

## 5. Children's Privacy

ShowMeOutfit does not knowingly collect personal data from children under the age of 13. The app is a general-audience weather and clothing advice tool. Age band inputs in the onboarding screen are derived solely into generic comfort temperature buffers ($\pm 1^\circ\text{C}$) and stored on the device locally.

---

## 6. Security

All weather network communications are strictly enforced over Transport Layer Security (HTTPS/TLS). On-device storage is protected by standard Android sandbox file-system security.

---

## 7. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect product updates or legal compliance. Any changes will be published at this URL with an updated effective date.

---

## 8. Contact Us

If you have questions, feedback, or concerns regarding this Privacy Policy, please open an issue on our GitHub repository:
* **GitHub Repository:** [https://github.com/smoh-apps/ShowMeOutfit](https://github.com/smoh-apps/ShowMeOutfit)
* **Developer Support:** `vietnguyen.smoh@gmail.com`
