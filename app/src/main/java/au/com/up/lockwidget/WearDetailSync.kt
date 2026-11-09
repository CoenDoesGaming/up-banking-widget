package au.com.up.lockwidget

import android.content.Context
import android.util.Log
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.GZIPOutputStream

/**
 * Builds the richer payload for the Wear OS app (Activity, 2Up and Savers pages) and pushes it
 * to the watch inside the `/up/snapshot` data item (see [WearSync]). The Personal Access Token never leaves the phone.
 *
 * Balances refresh on every widget sync, reusing the accounts list that sync already fetched.
 * Transaction lists cost one API call per account, so they are throttled to [TX_MAX_AGE_MS]
 * unless the watch asks for a refresh when the app opens. A 429 backs off exponentially.
 */
object WearDetailSync {
    const val PATH_REFRESH = "/up/refresh"

    private const val TAG = "WearDetailSync"
    private const val PREFS = "up_wear_detail_cache"
    private const val KEY_TX_JSON = "tx_json"
    private const val KEY_TX_AT = "tx_at"
    private const val KEY_BACKOFF_UNTIL = "backoff_until"
    private const val KEY_BACKOFF_STEP = "backoff_step"
    private const val KEY_LAST_PAYLOAD = "last_payload"
    private const val KEY_DETAIL_GZ = "detail_gz"

    private const val TX_PER_ACCOUNT = 20
    private const val TX_MAX_AGE_MS = 10 * 60_000L
    private const val TX_MIN_GAP_FORCED_MS = 30_000L
    private const val BACKOFF_BASE_MS = 60_000L
    private const val BACKOFF_MAX_MS = 30 * 60_000L

    private val lock = Any()

    private data class Account(
        val id: String,
        val name: String,
        val type: String,
        val ownership: String,
        val balanceCents: Long
    )

    /**
     * @param accountsData the `data` array from GET /accounts if the caller already has it.
     * @param force true when the watch app opened and asked for fresh data.
     */
    fun sync(context: Context, accountsData: JSONArray?, force: Boolean) {
        val appContext = context.applicationContext
        synchronized(lock) {
            runCatching { syncLocked(appContext, accountsData, force) }
                .onFailure { Log.w(TAG, "Watch detail sync failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun syncLocked(context: Context, givenAccounts: JSONArray?, force: Boolean) {
        val token = UpPreferences.getToken(context).trim()
        if (token.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val backoffUntil = prefs.getLong(KEY_BACKOFF_UNTIL, 0L)
        val inBackoff = now < backoffUntil

        val accountsData = givenAccounts ?: run {
            if (inBackoff) return
            try {
                fetchAllAccounts(token)
            } catch (e: UpApiClient.RateLimitedException) {
                startBackoff(context, now)
                return
            }
        }
        val accounts = parseAccounts(accountsData)

        // Only the main individual Spending account on the Activity page; 2Up gets its own page.
        val spending = accounts.firstOrNull { it.type == "TRANSACTIONAL" && it.ownership == "INDIVIDUAL" }
            ?: accounts.firstOrNull { it.type == "TRANSACTIONAL" }
        val joint = accounts.firstOrNull {
            it.type == "TRANSACTIONAL" && it.ownership == "JOINT" && it.id != spending?.id
        }
        // HOME_LOAN and any other types are left out
        val savers = accounts.filter { it.type == "SAVER" }
        val wanted = listOfNotNull(spending, joint) + savers

        val txCache = runCatching { JSONObject(prefs.getString(KEY_TX_JSON, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val txAt = prefs.getLong(KEY_TX_AT, 0L)
        val age = now - txAt
        val missing = wanted.any { !txCache.has(it.id) }
        val shouldFetchTx = !inBackoff && when {
            force -> age > TX_MIN_GAP_FORCED_MS
            else -> age > TX_MAX_AGE_MS || missing
        }

        if (shouldFetchTx) {
            var rateLimited = false
            for (account in wanted) {
                try {
                    txCache.put(account.id, fetchTransactions(token, account.id))
                } catch (e: UpApiClient.RateLimitedException) {
                    rateLimited = true
                    break
                } catch (e: Exception) {
                    // Keep whatever was cached for this account
                }
            }
            if (rateLimited) {
                startBackoff(context, now)
            } else {
                prefs.edit()
                    .putLong(KEY_TX_AT, now)
                    .putLong(KEY_BACKOFF_STEP, 0)
                    .putLong(KEY_BACKOFF_UNTIL, 0)
                    .apply()
            }
            // Drop accounts that no longer exist (closed savers)
            val keep = wanted.map { it.id }.toSet()
            txCache.keys().asSequence().toList().filter { it !in keep }.forEach { txCache.remove(it) }
            prefs.edit().putString(KEY_TX_JSON, txCache.toString()).apply()
        }

        fun accountJson(a: Account) = JSONObject()
            .put("id", a.id)
            .put("name", a.name)
            .put("bal", a.balanceCents)
            .put("own", a.ownership)
            .put("tx", txCache.optJSONArray(a.id) ?: JSONArray())

        val payload = JSONObject()
            .put("v", 1)
            .put("txAt", prefs.getLong(KEY_TX_AT, 0L))
            .put("spending", spending?.let(::accountJson) ?: JSONObject.NULL)
            .put("joint", joint?.let(::accountJson) ?: JSONObject.NULL)
            .put("savers", JSONArray(savers.map(::accountJson)))

        // Skip the Bluetooth hop when nothing changed, unless the watch is waiting on a refresh
        val body = payload.toString()
        // Also require a cached payload: a phone upgraded from v1.2.1 has KEY_LAST_PAYLOAD but none
        if (!force && body == prefs.getString(KEY_LAST_PAYLOAD, null) && prefs.contains(KEY_DETAIL_GZ)) return
        prefs.edit().putString(KEY_LAST_PAYLOAD, body).apply()

        payload.put("syncedAt", now)
        prefs.edit()
            .putString(KEY_DETAIL_GZ, Base64.encodeToString(gzip(payload.toString()), Base64.NO_WRAP))
            .commit()
        // Ride along on the urgent /up/snapshot item the complications already use. A separate,
        // non-urgent data item could sit undelivered for up to 30 minutes.
        WearSync.pushSnapshot(context)
    }

    /** Latest gzipped payload, attached to every snapshot push by [WearSync]. */
    fun cachedPayload(context: Context): ByteArray? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DETAIL_GZ, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }

    private fun startBackoff(context: Context, now: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val step = prefs.getLong(KEY_BACKOFF_STEP, 0L).coerceAtMost(10)
        val delay = (BACKOFF_BASE_MS shl step.toInt()).coerceAtMost(BACKOFF_MAX_MS)
        prefs.edit()
            .putLong(KEY_BACKOFF_STEP, step + 1)
            .putLong(KEY_BACKOFF_UNTIL, now + delay)
            .apply()
        Log.w(TAG, "Rate limited by Up API, backing off ${delay / 1000}s")
    }

    private fun fetchAllAccounts(token: String): JSONArray {
        val all = JSONArray()
        var next: String? = "${UpApiClient.BASE_URL}/accounts?page%5Bsize%5D=100"
        while (next != null) {
            val json = UpApiClient.getJson(next, token)
            val data = json.optJSONArray("data") ?: break
            for (i in 0 until data.length()) all.put(data.get(i))
            // Opaque cursor: follow links.next until it is null
            next = json.optJSONObject("links")?.let {
                if (it.isNull("next")) null else it.optString("next").ifBlank { null }
            }
        }
        return all
    }

    /** Newest first, capped at [TX_PER_ACCOUNT]. One page is enough, so no cursor following. */
    private fun fetchTransactions(token: String, accountId: String): JSONArray {
        val json = UpApiClient.getJson(
            "${UpApiClient.BASE_URL}/accounts/$accountId/transactions?page%5Bsize%5D=$TX_PER_ACCOUNT",
            token
        )
        val data = json.optJSONArray("data") ?: return JSONArray()
        val out = JSONArray()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val transferId = item.optJSONObject("relationships")
                ?.optJSONObject("transferAccount")
                ?.optJSONObject("data")
                ?.optString("id")
                ?.ifBlank { null }
            val tx = JSONObject()
                .put("d", attrs.optString("description", "Transaction"))
                .put("a", attrs.optJSONObject("amount")?.optLong("valueInBaseUnits", 0L) ?: 0L)
                .put("s", attrs.optString("status", "SETTLED"))
                .put("t", attrs.optString("createdAt"))
            if (transferId != null) tx.put("x", transferId)
            attrs.optString("message").takeIf { it.isNotBlank() && it != "null" }?.let { tx.put("m", it) }
            out.put(tx)
        }
        return out
    }

    private fun parseAccounts(data: JSONArray): List<Account> {
        val out = mutableListOf<Account>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val balance = attrs.optJSONObject("balance")
            val cents = balance?.optLong("valueInBaseUnits", Long.MIN_VALUE)
                ?.takeIf { it != Long.MIN_VALUE }
                ?: runCatching {
                    BigDecimal(balance?.optString("value", "0") ?: "0").movePointRight(2).toLong()
                }.getOrDefault(0L)
            out.add(
                Account(
                    id = item.optString("id"),
                    // Keep the emoji: it is part of displayName and the watch shows it
                    name = attrs.optString("displayName", "Account").trim(),
                    type = attrs.optString("accountType").uppercase(),
                    ownership = attrs.optString("ownershipType").uppercase(),
                    balanceCents = cents
                )
            )
        }
        return out
    }

    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }
}
