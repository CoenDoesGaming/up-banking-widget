# Google Play submission checklist

Everything Play Console asks for, in the order it asks. Draft answers are based on what the code actually does; double-check anything marked **Decide**.

## 1. Create the app

- **App name:** Widgets for Up
- **Default language:** English (Australia)
- **App or game:** App · **Free or paid:** Free
- **Package name** (set by the first upload, permanent): `au.com.up.lockwidget`

## 2. Upload

Build with `./gradlew bundleRelease`, then upload to an **Internal testing** track first:

- Phone: `app/build/outputs/bundle/release/app-release.aab`
- Watch: `wear/build/outputs/bundle/release/wear-release.aab` (add **Wear OS** under *Release > Advanced settings > Form factors* first; it's reviewed separately)

When asked about app signing, choose **Let Google manage and protect your app signing key** and use `~/.android/up-banking-widget/release.jks` as the **upload key**. Play installs and GitHub installs are then signed differently and can't update each other.

## 3. Store listing

**Short description (80 chars max):**
> Unofficial lock screen and Wear OS widgets for your Up bank balances.

**Full description:**
> Widgets for Up shows your Up balances where you can see them at a glance, without opening the app.
>
> • Lock screen and Always-On Display widgets for your Spending balance, Savers and a monthly category budget
> • Wear OS app with your recent transactions, 2Up account and Savers, plus watch face complications
> • Balances round down to the whole dollar, so a widget never shows more than you have
> • Tap any widget to open the Up app
>
> Works with your Up Personal Access Token, which you can create in the Up app under Data sharing. Your token is stored encrypted on your phone and only ever sent to Up's API. No analytics, no ads, no servers.
>
> This is an unofficial, open-source app. It is not made by, affiliated with or endorsed by Up or Bendigo and Adelaide Bank.

**Graphics needed:** 512×512 icon (export from `app/src/main/res/mipmap-xxxhdpi/` art), 1024×500 feature graphic, at least 2 phone screenshots, and Wear OS screenshots if publishing the watch app. Avoid Up's logo or app screenshots in these.

## 4. App content

| Section | Answer |
|---|---|
| Privacy policy | `https://github.com/CoenDoesGaming/up-banking-widget/blob/main/PRIVACY.md` |
| Ads | No ads |
| App access | Restricted. Reviewers need an Up Personal Access Token. **Decide:** provide one from a test account, or explain the app shows a setup screen without one. Never give a token for your real account. |
| Content rating | Questionnaire: Utility, no user-generated content, no purchases → likely "Everyone" |
| Target audience | 18+ (banking) |
| Financial features | Declare: views account balances and transactions from a bank via the user's own API token. No payments, loans or transfers. |
| Government / news / health | Not applicable |

## 5. Data safety

Google counts data as "collected" when it's sent off the device. This app's only outbound traffic is to Up's API, plus phone-to-watch sync.

- **Does your app collect or share any required user data types?** **Decide.** The app downloads your financial data from Up but never sends it to you or anyone else. The one thing it sends off the device is your token, to Up, to authenticate. Many apps that only call the user's own bank API answer **No**. If you want to be conservative, declare *Financial info → Other financial info* as collected, **not shared**, used for **App functionality**, processed **ephemerally** = No, and **required**.
- **Is all user data encrypted in transit?** Yes (HTTPS to `api.up.com.au`; Wear Data Layer is encrypted).
- **Can users request deletion?** Yes: uninstalling deletes everything. Link to the "Deleting your data" section of the privacy policy.

## 6. Permissions declarations

| Permission | Status | Notes |
|---|---|---|
| `USE_EXACT_ALARM` | **Removed** | Play only allows it for alarm and calendar apps. |
| `SCHEDULE_EXACT_ALARM` | Kept, no form needed | Off by default on Android 14+. The app has an "Allow 1-Minute Sync" button; without it, sync falls back to roughly every 15 minutes while locked. |
| Notification listener | In-app disclosure added | The app shows a disclosure dialog before opening the notification access screen. Mention in the review notes that it only checks the posting package name. |

## 7. Remaining risk

- **Name and branding.** "Widgets for Up" still uses Up's name. Keep the listing clearly unofficial and avoid Up's logo. If Play rejects for impersonation, the fallback is a name without "Up".
- **Package ID** `au.com.up.lockwidget` uses Up's web domain reversed. Play may flag it, and it can't be changed after the first upload.
