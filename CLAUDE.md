# Up Wear OS app: handoff notes

Context for Claude Code, carried over from a planning chat. Nothing has been built for this feature yet.

## Existing setup
- Android phone app that calls the Up API and shows the spending balance in home screen widgets.
- Wear OS companion app with a complication showing the balance. The phone app refreshes it and sends data to the watch (presumably via the Wear Data Layer; check the existing code to confirm).
- The phone holds the Personal Access Token. The watch should never hold the token or call the API.

## Goal
Add a small Wear OS app with two pages, built from the existing phone-to-watch sync.

1. **Activity tab**: current spending balance at the top, recent transactions listed below.
2. **Savers tab**: list of all savers with name and balance. Tapping a saver opens a detail screen showing that saver's balance and its transaction history.

Visual style: compact version of the Up app. Near-black background (OLED friendly), coral for balances and accents, yellow used sparingly. Colours can be sampled from Up's screenshots.

## Up API facts (https://developer.up.com.au)
- Base URL: `https://api.up.com.au/api/v1`
- Auth header: `Authorization: Bearer <token>` (check with `GET /util/ping`)
- `GET /accounts` supports `filter[accountType]` (`SAVER`, `TRANSACTIONAL`, `HOME_LOAN`) and `filter[ownershipType]` (`INDIVIDUAL`, `JOINT`).
- Account attributes: `displayName` (emoji is part of the string), `balance.value` (string), `balance.valueInBaseUnits` (cents), `accountType`, `ownershipType`, `createdAt`.
- `GET /accounts/{id}/transactions` lists newest first. Supports `page[size]` (max about 100), `filter[status]` (`HELD`/`SETTLED`), `filter[since]`/`filter[until]` (RFC 3339), `filter[category]`, `filter[tag]`.
- Transaction attributes used: `description`, `amount.value`, `amount.valueInBaseUnits`, `status`, `createdAt`, `settledAt`, `message`, `roundUp`, plus relationship `transferAccount` (non-null for transfers between accounts; sign of `amount` gives direction).
- Pagination uses opaque cursors. Follow `links.next` until it is `null`. Do not build page numbers.
- 429 means rate limited; back off exponentially. No numeric limits are documented, so avoid refetching on every swipe.
- URL-encode query params (a `+` must be `%2B`).
- The API is read-only for money: no transfers or payments. Only categories, tags and webhooks are writable. Transfers must happen in the Up app.
- Not exposed by the API: savings goals and targets, interest rates, merchant logos.

## Proposed architecture
- Phone app is the data hub: it fetches accounts and transactions, then pushes a structured payload to the watch.
- Watch renders purely from locally stored data, so it opens instantly and works when the phone is out of range.
- Use `DataClient` for synced state (latest accounts and transactions) and `MessageClient` for one-off "refresh now" requests when the watch app opens.
- Reuse the existing refresh mechanism (WorkManager or similar) that already updates the widgets and complication. One refresh should update widgets, complication and watch app together.

### Payload sketch
- Spending account: id, balance.
- Savers: list of id, displayName (emoji and name), balance.
- Transactions per account (cap about 20 each): description, amount, status, createdAt, transferAccount id (nullable).
- `lastSyncedAt` timestamp.

Alternative for saver history: fetch on demand via a message to the phone. This is lighter on sync but needs a loading state and fails when the phone is out of range. Preloading about 20 transactions per saver is the recommended default.

## UI plan (Compose for Wear OS)
- Two-page horizontal pager with page indicator dots. Tab labels will not fit on a round screen.
- Activity page: balance on top, then a `ScalingLazyColumn` of transactions with small date headers. Mark `HELD` transactions. Show the saver name or emoji for transfers.
- Savers page: optional total saved at top, then compact chips with emoji, name and balance.
- Saver detail: pushed screen (swipe-to-dismiss to go back) with balance on top and transaction list below.
- Show a subtle "last synced" indicator so stale data is obvious.

## Edge cases to handle
- 2Up (joint) accounts appear as extra transactional accounts. Pick the intended one, e.g. `filter[ownershipType]=INDIVIDUAL`.
- A home loan, if present, appears as `HOME_LOAN`. Filter by type.
- Transactions that were once held can have a settled `amount` different from `holdInfo.amount`.
- Saver deposits, withdrawals and interest all appear as transactions.
- Keep the token in encrypted storage on the phone only. Never log it or hardcode it.

## Suggested first steps for Claude Code
1. Read the existing phone and watch modules to see how balance data is currently synced to the watch and complication.
2. Define the shared payload model and extend the phone-side sync to send accounts, savers and capped transaction lists.
3. Add watch-side storage and a repository that reads the synced payload.
4. Build the Activity page, then the Savers page, then the saver detail screen.
5. Add "refresh on open" via `MessageClient` and the last-synced indicator.
6. Make the complication and tile read from the same stored data.

## Decisions (resolved open questions)
- Existing sync: `WearSync.kt` pushes a flat `DataMap` of strings to `/up/snapshot` via `DataClient` for the complications. Left unchanged.
- Activity page shows only the main INDIVIDUAL Spending account. A 2Up (JOINT transactional) account gets its own page, shown only when one exists.
- Saver transactions are preloaded (20 per account) so the watch app loads instantly.

## As built
- Phone: `WearDetailSync.kt` pushes a gzipped JSON payload to `/up/detail`. Balances reuse the widget sync's `/accounts` fetch, so they update every sync. Transaction lists cost one API call per account, so they refresh at most every 10 minutes, or every 30 seconds at most when the watch asks. A 429 triggers exponential backoff (1 minute doubling, capped at 30 minutes). The push is skipped when nothing changed.
- Phone: `PhoneWearListenerService` handles `/up/refresh` messages from the watch.
- Watch: `DetailStore.kt` stores the payload. `WearUi.kt` holds the Compose UI (pager with Activity / 2Up / Savers pages, plus the saver detail route). `MainActivity` sends `/up/refresh` on start and shows a "Synced x min ago" footer.
- Not done: a tile. Complications still read `/up/snapshot`, which comes from the same sync.
- Note: the token is currently stored in plain SharedPreferences (`UpPreferences`), not encrypted storage.
