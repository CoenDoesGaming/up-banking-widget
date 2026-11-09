# Up Banking Lock Screen Widgets (Samsung One UI)

An open-source Android app that brings **Up Bank** Lock Screen and Always-On Display (AOD) widgets to **Samsung One UI (One UI 7+)** devices, powered by the official [Up Banking API](https://developer.up.com.au/).

---

## ✨ Features & Included Widgets

Includes **6 Lock Screen & AOD widgets** across three widget families (available in both **1x1 Tiny** and **2x1 Small** sizes):

### 1. Activity (`1x1` & `2x1`)
* **Activity (1x1)**: Shows the `Up` badge and your primary Spending account balance rounded to the nearest whole dollar (e.g., `$123`).
* **Activity (2x1)**: Shows your Spending balance alongside the merchant/description and amount of your most recent Spending transaction (e.g., `Netflix • $19.99`).

### 2. Savers (`1x1` & `2x1`)
* **Savers (1x1)**: Shows `Savers` (or your selected Saver account name) and the whole-dollar balance (e.g., `$543`).
* **Savers (2x1)**: Shows your Saver balance plus the latest transfer or activity description and signed amount (e.g., `From Spending • +$5`).
* Choose between **All Savers** (combined balance across all your Savers) or any **individual Saver** inside the app.

### 3. Category Tracker (`1x1` & `2x1`)
* **Category Tracker (1x1)**: Renders a live circular progress ring around the 3-letter category code (e.g., `GRO`) and your remaining monthly budget (e.g., `$56`).
* **Category Tracker (2x1)**: Shows the category name (e.g., `Groceries`) and remaining budget (`$56 left`).
* Pick any official Up spending category and set your monthly budget target inside the app.

### 🔄 Automatic Background Updates (Even While Locked)
* **1-Minute Background Sync (`ELAPSED_REALTIME_WAKEUP`)**: Automatically wakes a partial background worker every **60 seconds** (and on Lock Screen wake) to fetch your latest balances and transactions and push them directly to your Lock Screen and AOD widgets—even while your phone stays locked.
* **Real-Time Instant Sync (Optional)**: Enable **Up Widget Instant Sync** under Notification Access inside the app so your Lock Screen widgets also update within 1.5 seconds whenever the official **Up** app posts a card purchase or transfer notification, and immediately whenever your Lock Screen turns on (`ACTION_SCREEN_ON`).

### 🚀 Tap to Open Up
Tapping any widget on your Lock Screen prompts you to unlock your phone, refreshes your widget balances in the background, and opens the official **Up** app (`au.com.up.money`).

### ⌚ Wear OS Complications
A companion watch app adds three complications for your watch face:

| Complication | Types |
|---|---|
| **Up Activity** | Short text (`Up $123`) and long text (latest transaction) |
| **Up Savers** | Short text and long text |
| **Up Category Tracker** | Short text, long text, and a progress ring |

The phone app sends already-synced data to the watch over the **Wear Data Layer** after every sync. Your Personal Access Token is **never** sent to the watch, and the watch makes no network calls of its own.

### ⌚ Wear OS App
Open **Up** from the watch's app list and swipe between pages:
* **Activity**: your main (individual) Spending balance and its 20 most recent transactions, grouped by day. Pending transactions are marked, and transfers show which of your accounts the money moved to or from.
* **2Up**: the same view for your 2Up joint Spending account. This page only appears if you have one.
* **Savers**: total saved, then each Saver with its balance. Tap one to see its 20 most recent transactions, and swipe right to go back.

The watch shows its stored data straight away, even with the phone out of range, and asks the phone for a fresh sync each time it opens. Balances update with every phone sync. Transaction lists refresh at most every 10 minutes in the background (or when you open the watch app) to stay well clear of Up's rate limits.

**Watch setup:**
1. Install `up-banking-widget-wear-v1.2.0.apk` on your watch (e.g. `adb -s <watch> install up-banking-widget-wear-v1.2.0.apk`).
2. Open the phone app and tap **Save Settings & Sync All Widgets**.
3. On the watch, long-press the watch face → **Edit** → choose an **Up** complication.

Requires a watch paired with the phone, and both APKs from the same release (they share a package name and signing key).

---

## 📲 Installation & Setup

1. **Download the APK**: Download the latest `.apk` from the [**Releases**](../../releases) page and install it on your Samsung Galaxy device.
2. **Get Your Up Personal Access Token**:
   * Open the **Up** app on your phone.
   * Go to **Data sharing** → **Personal Access Token** (or visit [api.up.com.au](https://api.up.com.au/)).
   * Generate a token (`up:yeah:...`) and copy it.
3. **Configure the App**:
   * Open the **Up** widget companion app.
   * Paste your **Personal Access Token**, select your preferred Saver and Category Tracker budget, and tap **Save Settings & Sync All Widgets**.
4. **Add Widgets to Your Samsung Lock Screen**:
   * Long-press your **Lock Screen** (or open *Settings → Wallpaper and style → Lock screen*).
   * Tap the widgets area beneath the clock.
   * Select **Up** and add any of the `1x1` or `2x1` **Activity**, **Savers**, or **Category Tracker** widgets.

---

## 🛠️ Building from Source

1. Clone this repository:
   ```bash
   git clone https://github.com/CoenDoesGaming/up-banking-widget.git
   cd up-banking-widget
   ```
2. Open the project in **Android Studio** (or build via Gradle):
   ```bash
   ./gradlew assembleDebug
   ```
3. The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔒 Privacy & Security
* Your **Up Personal Access Token** is stored locally on your device in private app preferences (`SharedPreferences`) and is only ever sent directly to `https://api.up.com.au/api/v1` over HTTPS.
* No third-party servers, analytics, or tracking are included.

---

## 🙏 Credits
* Lock Screen & One UI widget metadata integration based on research and documentation from [`thatjoshguy67/blur-widget-demo`](https://github.com/thatjoshguy67/blur-widget-demo/wiki/Lock-Screen-Widgets).
* Banking data powered by the [Up Bank API](https://developer.up.com.au/).
