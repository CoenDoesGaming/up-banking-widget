# Privacy Policy — Widgets for Up

_Last updated: 19 November 2026_

Widgets for Up ("the app") is an unofficial, open-source app that shows your Up bank balances in Android lock screen widgets and on a Wear OS watch. It is not made by, affiliated with or endorsed by Up or Bendigo and Adelaide Bank.

## What the app accesses

- **Your Up Personal Access Token**, which you type into the app. It is stored on your phone, encrypted with a key held in the Android Keystore, and is only ever sent to Up's API (`api.up.com.au`) to read your account data.
- **Your Up account data**: account names, balances and recent transactions, fetched from Up's API using your token. It is used only to draw the widgets, the watch face complications and the watch app.
- **Notification access (optional).** If you turn on instant sync, the app checks which app posted each notification. When the Up app posts one, it refreshes your balances. It never reads, stores or sends the content of any notification.

## Where your data goes

- Your token and account data **stay on your devices**. The only network requests the app makes are to Up's API.
- If you use the Wear OS app, your phone sends your balances and recent transactions to your paired watch over the Wear OS Data Layer (Bluetooth or Wi-Fi). Your token is never sent to the watch.
- The developer does not run any servers and never receives your token or financial data.
- The app contains no analytics, advertising or tracking.
- Cloud backup is turned off, so your data isn't copied to your Google account.

## Deleting your data

Uninstall the app from your phone and watch to delete everything it has stored. To revoke the token itself, generate a new Personal Access Token in the Up app, which invalidates the old one.

## Contact

Questions about this policy: open an issue at https://github.com/CoenDoesGaming/up-banking-widget/issues
