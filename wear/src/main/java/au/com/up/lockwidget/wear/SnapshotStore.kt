package au.com.up.lockwidget.wear

import android.content.Context

/** Local cache of the last snapshot pushed from the phone over the Wear Data Layer. */
object SnapshotStore {
    private const val PREFS = "up_wear_snapshot"

    data class Snapshot(
        val spendingName: String,
        val spendingBalance: String,
        val activityTxDesc: String,
        val activityTxAmount: String,
        val saverTitle: String,
        val saverBalance: String,
        val saverTxDesc: String,
        val saverTxAmount: String,
        val categoryName: String,
        val categoryCode: String,
        val categoryLeftWhole: String,
        val categoryLeftLabel: String,
        val categoryProgress: Float,
        val updatedAt: Long
    )

    fun save(context: Context, s: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("spendingName", s.spendingName)
            .putString("spendingBalance", s.spendingBalance)
            .putString("activityTxDesc", s.activityTxDesc)
            .putString("activityTxAmount", s.activityTxAmount)
            .putString("saverTitle", s.saverTitle)
            .putString("saverBalance", s.saverBalance)
            .putString("saverTxDesc", s.saverTxDesc)
            .putString("saverTxAmount", s.saverTxAmount)
            .putString("categoryName", s.categoryName)
            .putString("categoryCode", s.categoryCode)
            .putString("categoryLeftWhole", s.categoryLeftWhole)
            .putString("categoryLeftLabel", s.categoryLeftLabel)
            .putFloat("categoryProgress", s.categoryProgress)
            .putLong("updatedAt", s.updatedAt)
            .apply()
    }

    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            spendingName = p.getString("spendingName", "Spending") ?: "Spending",
            spendingBalance = p.getString("spendingBalance", "--") ?: "--",
            activityTxDesc = p.getString("activityTxDesc", "Open phone app") ?: "Open phone app",
            activityTxAmount = p.getString("activityTxAmount", "") ?: "",
            saverTitle = p.getString("saverTitle", "Savers") ?: "Savers",
            saverBalance = p.getString("saverBalance", "--") ?: "--",
            saverTxDesc = p.getString("saverTxDesc", "") ?: "",
            saverTxAmount = p.getString("saverTxAmount", "") ?: "",
            categoryName = p.getString("categoryName", "Category") ?: "Category",
            categoryCode = p.getString("categoryCode", "CAT") ?: "CAT",
            categoryLeftWhole = p.getString("categoryLeftWhole", "--") ?: "--",
            categoryLeftLabel = p.getString("categoryLeftLabel", "-- left") ?: "-- left",
            categoryProgress = p.getFloat("categoryProgress", 0f),
            updatedAt = p.getLong("updatedAt", 0L)
        )
    }
}
