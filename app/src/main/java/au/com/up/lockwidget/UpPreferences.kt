package au.com.up.lockwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UpPreferences {
    private const val PREFS_NAME = "up_lock_widget_prefs"
    private const val KEY_TOKEN = "personal_access_token"
    private const val KEY_LAST_UPDATED = "last_updated"

    // Activity (Spending + Recent Transaction)
    private const val KEY_ACCOUNT_NAME = "account_name"
    private const val KEY_BALANCE_FULL = "balance_full"
    private const val KEY_BALANCE_COMPACT = "balance_compact"
    private const val KEY_ACTIVITY_TX_DESC = "activity_tx_desc"
    private const val KEY_ACTIVITY_TX_AMOUNT = "activity_tx_amount"

    // Savers (Total or Single Saver + Recent Transaction)
    private const val KEY_SELECTED_SAVER_ID = "selected_saver_id"
    private const val KEY_SAVERS_LIST_JSON = "savers_list_json"
    private const val KEY_SAVER_TITLE = "saver_title"
    private const val KEY_SAVER_BALANCE_WHOLE = "saver_balance_whole"
    private const val KEY_SAVER_BALANCE_FULL = "saver_balance_full"
    private const val KEY_SAVER_TX_DESC = "saver_tx_desc"
    private const val KEY_SAVER_TX_AMOUNT = "saver_tx_amount"

    // Category Tracker
    private const val KEY_CATEGORY_ID = "category_id"
    private const val KEY_CATEGORY_NAME = "category_name"
    private const val KEY_CATEGORY_BUDGET = "category_budget"
    private const val KEY_CATEGORY_LEFT_WHOLE = "category_left_whole"
    private const val KEY_CATEGORY_LEFT_LABEL = "category_left_label"
    private const val KEY_CATEGORY_PROGRESS = "category_progress"

    /**
     * Leave empty for public builds; users enter their Personal Access Token inside the app UI.
     */
    const val DEFAULT_PERSONAL_ACCESS_TOKEN = ""

    data class SaverOption(val id: String, val name: String)
    data class CategoryOption(val id: String, val name: String)

    val DEFAULT_CATEGORIES = listOf(
        CategoryOption("groceries", "Groceries"),
        CategoryOption("restaurants-and-cafes", "Restaurants & Cafes"),
        CategoryOption("takeaway", "Takeaway"),
        CategoryOption("pubs-and-bars", "Pubs & Bars"),
        CategoryOption("fuel", "Fuel"),
        CategoryOption("public-transport", "Public Transport"),
        CategoryOption("taxis-and-share-cars", "Taxis & Share Cars"),
        CategoryOption("clothing-and-accessories", "Clothing & Accessories"),
        CategoryOption("entertainment", "Entertainment"),
        CategoryOption("games-and-software", "Games & Software"),
        CategoryOption("health-and-medical", "Health & Medical"),
        CategoryOption("fitness-and-wellbeing", "Fitness & Wellbeing"),
        CategoryOption("homeware-and-appliances", "Homeware & Appliances"),
        CategoryOption("personal-care-and-beauty", "Personal Care & Beauty"),
        CategoryOption("pets", "Pets"),
        CategoryOption("technology", "Technology")
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getToken(context: Context): String {
        val saved = prefs(context).getString(KEY_TOKEN, null)?.trim().orEmpty()
        return if (saved.isNotEmpty()) saved else DEFAULT_PERSONAL_ACCESS_TOKEN.trim()
    }

    fun setToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun markUpdatedNow(context: Context) {
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        prefs(context).edit().putString(KEY_LAST_UPDATED, "Updated $timeFmt").apply()
    }

    fun getLastUpdated(context: Context): String =
        prefs(context).getString(KEY_LAST_UPDATED, "Tap to sync with Up") ?: "Tap to sync with Up"

    // --- Activity / Spending ---
    fun saveActivitySnapshot(
        context: Context,
        accountName: String,
        balanceFull: String,
        balanceCompact: String,
        txDesc: String,
        txAmount: String
    ) {
        prefs(context).edit()
            .putString(KEY_ACCOUNT_NAME, accountName)
            .putString(KEY_BALANCE_FULL, balanceFull)
            .putString(KEY_BALANCE_COMPACT, balanceCompact)
            .putString(KEY_ACTIVITY_TX_DESC, txDesc)
            .putString(KEY_ACTIVITY_TX_AMOUNT, txAmount)
            .apply()
    }

    fun getAccountName(context: Context): String =
        prefs(context).getString(KEY_ACCOUNT_NAME, "Spending") ?: "Spending"

    fun getBalanceFull(context: Context): String =
        prefs(context).getString(KEY_BALANCE_FULL, "$0") ?: "$0"

    fun getBalanceCompact(context: Context): String =
        prefs(context).getString(KEY_BALANCE_COMPACT, "$0") ?: "$0"

    fun getActivityTxDesc(context: Context): String =
        prefs(context).getString(KEY_ACTIVITY_TX_DESC, "Recent activity") ?: "Recent activity"

    fun getActivityTxAmount(context: Context): String =
        prefs(context).getString(KEY_ACTIVITY_TX_AMOUNT, "—") ?: "—"

    // --- Savers ---
    fun getSelectedSaverId(context: Context): String =
        prefs(context).getString(KEY_SELECTED_SAVER_ID, "ALL") ?: "ALL"

    fun setSelectedSaverId(context: Context, saverId: String) {
        prefs(context).edit().putString(KEY_SELECTED_SAVER_ID, saverId).apply()
    }

    fun saveSaversOptions(context: Context, options: List<SaverOption>) {
        val arr = JSONArray()
        for (opt in options) {
            arr.put(JSONObject().put("id", opt.id).put("name", opt.name))
        }
        prefs(context).edit().putString(KEY_SAVERS_LIST_JSON, arr.toString()).apply()
    }

    fun getSaversOptions(context: Context): List<SaverOption> {
        val result = mutableListOf(SaverOption("ALL", "All Savers"))
        val raw = prefs(context).getString(KEY_SAVERS_LIST_JSON, null) ?: return result
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (id.isNotEmpty() && id != "ALL") {
                    result.add(SaverOption(id, name))
                }
            }
        }
        return result
    }

    fun saveSaversSnapshot(
        context: Context,
        title: String,
        balanceWhole: String,
        balanceFull: String,
        txDesc: String,
        txAmount: String
    ) {
        prefs(context).edit()
            .putString(KEY_SAVER_TITLE, title)
            .putString(KEY_SAVER_BALANCE_WHOLE, balanceWhole)
            .putString(KEY_SAVER_BALANCE_FULL, balanceFull)
            .putString(KEY_SAVER_TX_DESC, txDesc)
            .putString(KEY_SAVER_TX_AMOUNT, txAmount)
            .apply()
    }

    fun getSaverTitle(context: Context): String =
        prefs(context).getString(KEY_SAVER_TITLE, "Savers") ?: "Savers"

    fun getSaverBalanceWhole(context: Context): String =
        prefs(context).getString(KEY_SAVER_BALANCE_WHOLE, "$0") ?: "$0"

    fun getSaverBalanceFull(context: Context): String =
        prefs(context).getString(KEY_SAVER_BALANCE_FULL, "$0") ?: "$0"

    fun getSaverTxDesc(context: Context): String =
        prefs(context).getString(KEY_SAVER_TX_DESC, "From Spending") ?: "From Spending"

    fun getSaverTxAmount(context: Context): String =
        prefs(context).getString(KEY_SAVER_TX_AMOUNT, "+$0") ?: "+$0"

    // --- Category Tracker ---
    fun getCategoryId(context: Context): String =
        prefs(context).getString(KEY_CATEGORY_ID, "groceries") ?: "groceries"

    fun getCategoryName(context: Context): String =
        prefs(context).getString(KEY_CATEGORY_NAME, "Groceries") ?: "Groceries"

    fun setCategorySelection(context: Context, id: String, name: String) {
        prefs(context).edit()
            .putString(KEY_CATEGORY_ID, id)
            .putString(KEY_CATEGORY_NAME, name)
            .apply()
    }

    fun getCategoryBudget(context: Context): Int =
        prefs(context).getInt(KEY_CATEGORY_BUDGET, 200)

    fun setCategoryBudget(context: Context, budgetDollars: Int) {
        prefs(context).edit().putInt(KEY_CATEGORY_BUDGET, budgetDollars.coerceAtLeast(1)).apply()
    }

    fun saveCategorySnapshot(
        context: Context,
        leftWhole: String,
        leftLabel: String,
        progress: Float
    ) {
        prefs(context).edit()
            .putString(KEY_CATEGORY_LEFT_WHOLE, leftWhole)
            .putString(KEY_CATEGORY_LEFT_LABEL, leftLabel)
            .putFloat(KEY_CATEGORY_PROGRESS, progress.coerceIn(0f, 1f))
            .apply()
    }

    fun getCategoryCode(context: Context): String {
        val letters = getCategoryName(context).filter { it.isLetter() }.uppercase(Locale.US)
        return if (letters.length >= 3) letters.take(3) else letters.padEnd(3, 'X')
    }

    fun getCategoryLeftWhole(context: Context): String =
        prefs(context).getString(KEY_CATEGORY_LEFT_WHOLE, "$200") ?: "$200"

    fun getCategoryLeftLabel(context: Context): String =
        prefs(context).getString(KEY_CATEGORY_LEFT_LABEL, "$200 left") ?: "$200 left"

    fun getCategoryProgress(context: Context): Float =
        prefs(context).getFloat(KEY_CATEGORY_PROGRESS, 1.0f).coerceIn(0f, 1f)
}
