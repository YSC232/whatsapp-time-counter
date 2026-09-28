# WhatsApp Time Counter

A small Android utility that shows a movable counter while WhatsApp is open.

It tracks:

- **Total WhatsApp usage time for the current calendar day**
- **How many times WhatsApp was opened today**
- Automatic reset at **midnight**
- A compact movable overlay: `HH:MM:SS | opens`
- Regular WhatsApp (`com.whatsapp`) and WhatsApp Business (`com.whatsapp.w4b`)
- The bubble position is remembered

The counter is displayed only while WhatsApp is in the foreground.

> This is an independent open-source project. It is not affiliated with, endorsed by, or sponsored by WhatsApp or Meta.

## Download

The current version is **v1.3**.

**Download v1.3:**  
https://github.com/YSC232/whatsapp-time-counter/actions/runs/36349342349/artifacts/10941652505

GitHub Actions downloads the build as a ZIP file. Extract it and install `app-debug.apk`.

> GitHub Actions artifacts expire. If the link above has expired, open the repository's **Actions** tab, choose the newest successful **Build Android APK** run, and download **WhatsApp-Time-Counter-APK**.

## Installation

### 1. Download and install the APK

1. Download the ZIP from the link above.
2. Extract the ZIP.
3. Open `app-debug.apk`.
4. Android may ask you to allow your browser/file manager to **Install unknown apps**. Allow it only for the app you are using to install this APK.
5. Complete the installation.

Because this APK is distributed directly rather than through Google Play, Android may show additional security warnings. Read them before continuing.

### 2. Enable the accessibility service

WhatsApp Time Counter uses an Android **Accessibility Service** to detect which app is currently in the foreground and to display the counter as an accessibility overlay.

Open:

**Settings → Accessibility → Installed apps / Installed services → WhatsApp Time Counter**

and enable the service.

Menu names vary by Android version and phone manufacturer. Search Settings for **Accessibility** or **WhatsApp Time Counter** if the path is different on your phone.

### 3. If Android says the setting is restricted

Android 13 and newer can restrict sensitive settings such as Accessibility for apps installed outside an app store.

Google's standard procedure is:

1. Open **Settings → Apps**.
2. Select **WhatsApp Time Counter**.
3. Open **More / ⋮**.
4. Choose **Allow restricted settings**.
5. Authenticate if Android asks for your PIN, pattern, or biometrics.
6. Return to **Accessibility** and enable **WhatsApp Time Counter**.

Google documents this behavior here:  
https://support.google.com/android/answer/12623953

Only allow restricted settings for an APK if you trust its source. Accessibility access is a sensitive Android capability.

#### Samsung / other Android variants

Manufacturers can change the Settings interface. On some Samsung/Android versions the **⋮ / Allow restricted settings** option may not initially appear.

If that happens:

1. First go to **Settings → Accessibility → Installed apps/services → WhatsApp Time Counter** and try to enable it.
2. If Android displays the restricted-setting warning, dismiss it.
3. Return to **Settings → Apps → WhatsApp Time Counter** and check again for **⋮ → Allow restricted settings**.
4. Then return to Accessibility and enable the service.

If your device still does not offer the option, the exact restriction can depend on the Android version, manufacturer security software, device-management policy, or advanced protection settings. Do not disable general device security protections just to install this app.

## Using the app

After the accessibility service is enabled, open WhatsApp.

A small bubble will appear in this form:

```
00:12:34 | 5
```

- Left: total WhatsApp time accumulated today.
- Right: number of WhatsApp opens today.
- Drag the bubble to move it.
- Its position is remembered.
- Leaving WhatsApp hides the bubble.
- Returning to WhatsApp continues today's accumulated time.
- Both daily counters reset at local midnight.

No separate "display over other apps" permission is required because the bubble uses an accessibility overlay.

## Updating

The current builds are debug APKs produced by GitHub Actions and **do not yet use a persistent release signing key**.

As a result, an APK from a newer build may not install directly over an older build because Android requires updates to have the same signing certificate. If Android reports that the app was not installed:

1. Uninstall the existing WhatsApp Time Counter.
2. Install the new APK.
3. Enable its accessibility service again.

Uninstalling clears the app's saved counter data and settings.

A persistent release-signing setup is a future improvement.

## Privacy

The app is designed to keep its usage counters locally on the device. It does not require an account.

The source code is public in this repository so users can inspect how the counter works.

Because the app uses Accessibility, Android may display strong warnings about the potential capabilities of accessibility services. Those warnings are normal for this type of sideloaded app and should still be taken seriously.

## Building from source

Requirements:

- JDK 17
- Gradle 8.7
- Android SDK / compile SDK 35

GitHub Actions also builds the APK automatically on pushes to `main`.

## Compatibility

- Minimum Android SDK: **26 (Android 8.0)**
- Target SDK: **35**
- Tested primarily with Samsung/Android during development
- Supports WhatsApp and WhatsApp Business package names listed above

Behavior of sideloading and restricted settings can vary between Android versions and manufacturers.

## Issues

If the timer or open count behaves incorrectly on your device, open a GitHub issue and include:

- Phone manufacturer/model
- Android version
- Whether you use WhatsApp or WhatsApp Business
- What you did immediately before the problem occurred
- What the counter showed versus what you expected

Do **not** post passwords, signing keys, private messages, phone numbers, or other sensitive information in an issue.
