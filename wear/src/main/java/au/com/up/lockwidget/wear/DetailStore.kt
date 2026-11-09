package au.com.up.lockwidget.wear

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.util.zip.GZIPInputStream

/**
 * Local copy of the detail payload the phone pushes at [PATH_DETAIL]: Spending, 2Up and Savers
 * with up to 20 recent transactions each. The watch renders only from this, never the network.
 */
object DetailStore {
    const val PATH_DETAIL = "/up/detail"
    const val PATH_REFRESH = "/up/refresh"

    private const val PREFS = "up_wear_detail"
    const val KEY_JSON = "json"

    data class Tx(
        val description: String,
        val cents: Long,
        val held: Boolean,
        val createdAt: OffsetDateTime?,
        val transferAccountId: String?,
        val message: String?
    )

    data class Account(
        val id: String,
        val name: String,
        val balanceCents: Long,
        val joint: Boolean,
        val transactions: List<Tx>
    )

    data class Detail(
        val spending: Account?,
        val joint: Account?,
        val savers: List<Account>,
        val syncedAt: Long
    ) {
        val allAccounts: List<Account> get() = listOfNotNull(spending, joint) + savers
        fun account(id: String): Account? = allAccounts.firstOrNull { it.id == id }
    }

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveCompressed(context: Context, bytes: ByteArray) {
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
        parse(json) ?: return // ignore anything malformed rather than wiping good data
        prefs(context).edit().putString(KEY_JSON, json).apply()
    }

    fun load(context: Context): Detail? =
        prefs(context).getString(KEY_JSON, null)?.let(::parse)

    private fun parse(json: String): Detail? = runCatching {
        val root = JSONObject(json)
        Detail(
            spending = root.optJSONObject("spending")?.let(::parseAccount),
            joint = root.optJSONObject("joint")?.let(::parseAccount),
            savers = root.optJSONArray("savers").objects().map(::parseAccount),
            syncedAt = root.optLong("syncedAt", 0L)
        )
    }.getOrNull()

    private fun parseAccount(o: JSONObject) = Account(
        id = o.optString("id"),
        name = o.optString("name", "Account"),
        balanceCents = o.optLong("bal", 0L),
        joint = o.optString("own") == "JOINT",
        transactions = o.optJSONArray("tx").objects().map { t ->
            Tx(
                description = t.optString("d", "Transaction"),
                cents = t.optLong("a", 0L),
                held = t.optString("s") == "HELD",
                createdAt = runCatching { OffsetDateTime.parse(t.optString("t")) }.getOrNull(),
                transferAccountId = t.optString("x").ifBlank { null },
                message = t.optString("m").ifBlank { null }
            )
        }
    )

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
