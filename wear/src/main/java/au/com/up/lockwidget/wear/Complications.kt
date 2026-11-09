package au.com.up.lockwidget.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

private fun text(s: String) = PlainComplicationText.Builder(s).build()

private fun joinNonBlank(a: String, b: String) =
    listOf(a, b).filter { it.isNotBlank() }.joinToString(" • ")

/** Activity: Spending balance + latest transaction. */
class ActivityComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, SnapshotStore.load(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(
            type,
            SnapshotStore.Snapshot(
                "Spending", "$123", "Netflix", "$19.99", "Savers", "$543", "From Spending",
                "+$5", "Groceries", "GRO", "$56", "$56 left", 0.28f, 0L
            )
        )

    private fun build(type: ComplicationType, s: SnapshotStore.Snapshot): ComplicationData? {
        val desc = text("Up ${s.spendingName} balance ${s.spendingBalance}")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(s.spendingBalance), desc)
                    .setTitle(text("Up")).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text(joinNonBlank(s.activityTxDesc, s.activityTxAmount).ifBlank { s.spendingName }),
                    desc
                ).setTitle(text(s.spendingBalance)).build()
            else -> null
        }
    }
}

/** Savers: combined or selected Saver balance + latest Saver transfer. */
class SaversComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, SnapshotStore.load(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(
            type,
            SnapshotStore.Snapshot(
                "Spending", "$123", "Netflix", "$19.99", "Savers", "$543", "From Spending",
                "+$5", "Groceries", "GRO", "$56", "$56 left", 0.28f, 0L
            )
        )

    private fun build(type: ComplicationType, s: SnapshotStore.Snapshot): ComplicationData? {
        val desc = text("Up ${s.saverTitle} balance ${s.saverBalance}")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(s.saverBalance), desc)
                    .setTitle(text(s.saverTitle.take(7))).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(
                    text(joinNonBlank(s.saverTxDesc, s.saverTxAmount).ifBlank { s.saverTitle }),
                    desc
                ).setTitle(text("${s.saverTitle} ${s.saverBalance}")).build()
            else -> null
        }
    }
}

/** Category Tracker: remaining monthly budget, as text or a progress ring. */
class CategoryComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, SnapshotStore.load(this))

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(
            type,
            SnapshotStore.Snapshot(
                "Spending", "$123", "Netflix", "$19.99", "Savers", "$543", "From Spending",
                "+$5", "Groceries", "GRO", "$56", "$56 left", 0.28f, 0L
            )
        )

    private fun build(type: ComplicationType, s: SnapshotStore.Snapshot): ComplicationData? {
        val desc = text("${s.categoryName}: ${s.categoryLeftLabel}")
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text(s.categoryLeftWhole), desc)
                    .setTitle(text(s.categoryCode)).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(text(s.categoryLeftLabel), desc)
                    .setTitle(text(s.categoryName)).build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = (s.categoryProgress * 100f).coerceIn(0f, 100f),
                    min = 0f,
                    max = 100f,
                    contentDescription = desc
                ).setText(text(s.categoryLeftWhole)).setTitle(text(s.categoryCode)).build()
            else -> null
        }
    }
}
