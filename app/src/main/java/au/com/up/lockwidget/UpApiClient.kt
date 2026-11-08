package au.com.up.lockwidget

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object UpApiClient {
    private const val BASE_URL = "https://api.up.com.au/api/v1"

    data class SyncSummary(
        val spendingName: String,
        val spendingBalanceWhole: String,
        val activityTxDesc: String,
        val activityTxAmount: String,
        val saverTitle: String,
        val saverBalanceWhole: String,
        val saverTxDesc: String,
        val saverTxAmount: String,
        val categoryName: String,
        val categoryLeftWhole: String,
        val categoryLeftLabel: String,
        val categoryProgress: Float
    )

    private data class ParsedAccount(
        val id: String,
        val cleanName: String,
        val accountType: String,
        val ownershipType: String,
        val balance: BigDecimal
    )

    fun syncAll(context: Context): Result<SyncSummary> {
        val token = UpPreferences.getToken(context).trim()
        if (token.isEmpty()) {
            return Result.failure(IllegalArgumentException("Personal Access Token is empty"))
        }

        return try {
            // 1. Fetch all accounts
            val accountsJson = getJson("$BASE_URL/accounts?page%5Bsize%5D=100", token)
            val dataArr = accountsJson.optJSONArray("data")
                ?: return Result.failure(IllegalStateException("No accounts returned by Up API"))

            val parsedAccounts = mutableListOf<ParsedAccount>()
            for (i in 0 until dataArr.length()) {
                val item = dataArr.getJSONObject(i)
                val id = item.optString("id")
                val attrs = item.optJSONObject("attributes") ?: continue
                val accountType = attrs.optString("accountType")
                val ownershipType = attrs.optString("ownershipType")
                val rawName = attrs.optString("displayName", "Account")
                val cleanName = stripLeadingSymbols(rawName).ifBlank { "Account" }
                val valueStr = attrs.optJSONObject("balance")?.optString("value", "0.00") ?: "0.00"
                parsedAccounts.add(
                    ParsedAccount(
                        id = id,
                        cleanName = cleanName,
                        accountType = accountType,
                        ownershipType = ownershipType,
                        balance = BigDecimal(valueStr)
                    )
                )
            }

            // Primary Spending (TRANSACTIONAL) account
            val spendingAccount = parsedAccounts.firstOrNull {
                it.accountType.equals("TRANSACTIONAL", ignoreCase = true) &&
                    it.ownershipType.equals("INDIVIDUAL", ignoreCase = true)
            } ?: parsedAccounts.firstOrNull {
                it.accountType.equals("TRANSACTIONAL", ignoreCase = true)
            } ?: parsedAccounts.firstOrNull()
                ?: return Result.failure(IllegalStateException("No Up accounts found"))

            val spendingWhole = formatWholeCurrency(spendingAccount.balance)

            // Latest transaction for Spending account
            var activityTxDesc = "No recent activity"
            var activityTxAmount = ""
            runCatching {
                val txJson = getJson(
                    "$BASE_URL/accounts/${spendingAccount.id}/transactions?page%5Bsize%5D=5",
                    token
                )
                val txData = txJson.optJSONArray("data")
                if (txData != null && txData.length() > 0) {
                    val firstTx = txData.getJSONObject(0).getJSONObject("attributes")
                    activityTxDesc = firstTx.optString("description", "Recent transaction")
                    val amtVal = BigDecimal(
                        firstTx.getJSONObject("amount").optString("value", "0.00")
                    )
                    activityTxAmount = formatTransactionCurrency(amtVal, signed = false)
                }
            }

            UpPreferences.saveActivitySnapshot(
                context = context,
                accountName = spendingAccount.cleanName,
                balanceFull = spendingWhole,
                balanceCompact = spendingWhole,
                txDesc = activityTxDesc,
                txAmount = activityTxAmount
            )

            // 2. Savers accounts + latest Saver transaction
            val saverAccounts = parsedAccounts.filter {
                it.accountType.equals("SAVER", ignoreCase = true)
            }
            UpPreferences.saveSaversOptions(
                context,
                saverAccounts.map { UpPreferences.SaverOption(it.id, it.cleanName) }
            )

            val selectedSaverId = UpPreferences.getSelectedSaverId(context)
            val chosenSaver = if (selectedSaverId == "ALL") {
                null
            } else {
                saverAccounts.firstOrNull { it.id == selectedSaverId }
            }

            val saverTitle = chosenSaver?.cleanName ?: "Savers"
            val saverBalance = chosenSaver?.balance
                ?: saverAccounts.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.balance) }
            val saverWhole = formatWholeCurrency(saverBalance)

            var saverTxDesc = "No recent transfers"
            var saverTxAmount = "$0"
            val txTargetSaver = chosenSaver ?: saverAccounts.firstOrNull()
            if (txTargetSaver != null) {
                runCatching {
                    val sTxJson = getJson(
                        "$BASE_URL/accounts/${txTargetSaver.id}/transactions?page%5Bsize%5D=5",
                        token
                    )
                    val sTxData = sTxJson.optJSONArray("data")
                    if (sTxData != null && sTxData.length() > 0) {
                        val firstTx = sTxData.getJSONObject(0).getJSONObject("attributes")
                        val rawDesc = firstTx.optString("description", "Transfer")
                        saverTxDesc = rawDesc
                            .replace("Transfer from ", "From ", ignoreCase = true)
                            .replace("Transfer to ", "To ", ignoreCase = true)
                            .replace("Cover to ", "To ", ignoreCase = true)
                            .replace("Forward to ", "To ", ignoreCase = true)
                        val amtVal = BigDecimal(
                            firstTx.getJSONObject("amount").optString("value", "0.00")
                        )
                        saverTxAmount = formatTransactionCurrency(amtVal, signed = true)
                    }
                }
            }

            UpPreferences.saveSaversSnapshot(
                context = context,
                title = saverTitle,
                balanceWhole = saverWhole,
                balanceFull = saverWhole,
                txDesc = saverTxDesc,
                txAmount = saverTxAmount
            )

            // 3. Category Tracker (current calendar month spending in chosen category)
            val categoryId = UpPreferences.getCategoryId(context)
            val categoryName = UpPreferences.getCategoryName(context)
            val budgetDollars = UpPreferences.getCategoryBudget(context).coerceAtLeast(1)

            var spentThisMonth = BigDecimal.ZERO
            runCatching {
                val startOfMonth = ZonedDateTime.now(ZoneId.systemDefault())
                    .withDayOfMonth(1)
                    .withHour(0)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                val encodedSince = URLEncoder.encode(startOfMonth, "UTF-8")
                val encodedCat = URLEncoder.encode(categoryId, "UTF-8")

                val catTxJson = getJson(
                    "$BASE_URL/transactions?filter%5Bcategory%5D=$encodedCat&filter%5Bsince%5D=$encodedSince&page%5Bsize%5D=100",
                    token
                )
                val catData = catTxJson.optJSONArray("data")
                if (catData != null) {
                    for (i in 0 until catData.length()) {
                        val txAttrs = catData.getJSONObject(i).optJSONObject("attributes") ?: continue
                        val vStr = txAttrs.optJSONObject("amount")?.optString("value", "0.00") ?: "0.00"
                        val v = BigDecimal(vStr)
                        if (v < BigDecimal.ZERO) {
                            spentThisMonth = spentThisMonth.add(v.abs())
                        }
                    }
                }
            }

            val budgetDecimal = BigDecimal(budgetDollars)
            val remaining = budgetDecimal.subtract(spentThisMonth)
            val remainingClamped = remaining.max(BigDecimal.ZERO)
            val leftWhole = formatWholeCurrency(remainingClamped)
            val leftLabel = if (remaining >= BigDecimal.ZERO) {
                "$leftWhole left"
            } else {
                "${formatWholeCurrency(remaining.abs())} over"
            }
            val progress = (remainingClamped.toFloat() / budgetDecimal.toFloat()).coerceIn(0f, 1f)

            UpPreferences.saveCategorySnapshot(
                context = context,
                leftWhole = leftWhole,
                leftLabel = leftLabel,
                progress = progress
            )
            UpPreferences.markUpdatedNow(context)

            Result.success(
                SyncSummary(
                    spendingName = spendingAccount.cleanName,
                    spendingBalanceWhole = spendingWhole,
                    activityTxDesc = activityTxDesc,
                    activityTxAmount = activityTxAmount,
                    saverTitle = saverTitle,
                    saverBalanceWhole = saverWhole,
                    saverTxDesc = saverTxDesc,
                    saverTxAmount = saverTxAmount,
                    categoryName = categoryName,
                    categoryLeftWhole = leftWhole,
                    categoryLeftLabel = leftLabel,
                    categoryProgress = progress
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getJson(urlString: String, token: String): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()

        if (responseCode !in 200..299) {
            val errorDetail = runCatching {
                val errJson = JSONObject(body)
                val errors = errJson.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    errors.getJSONObject(0).optString("detail", "HTTP $responseCode")
                } else {
                    "HTTP $responseCode"
                }
            }.getOrDefault("HTTP $responseCode")
            throw IllegalStateException(errorDetail)
        }
        return JSONObject(body)
    }

    private fun stripLeadingSymbols(raw: String): String {
        return raw.replace(Regex("^[^\\p{L}\\p{N}$]+"), "").trim()
    }

    fun formatWholeCurrency(amount: BigDecimal): String {
        val rounded = amount.setScale(0, RoundingMode.HALF_UP)
        val sign = if (rounded.signum() < 0) "-" else ""
        val formattedWhole = NumberFormat.getIntegerInstance(Locale.US).format(rounded.abs())
        return "$sign$$formattedWhole"
    }

    fun formatTransactionCurrency(amount: BigDecimal, signed: Boolean): String {
        val absVal = amount.abs()
        val isWhole = absVal.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
        val numberPart = if (isWhole) {
            NumberFormat.getIntegerInstance(Locale.US).format(absVal.setScale(0, RoundingMode.HALF_UP))
        } else {
            val fmt = NumberFormat.getNumberInstance(Locale.US)
            fmt.minimumFractionDigits = 2
            fmt.maximumFractionDigits = 2
            fmt.format(absVal)
        }
        val prefix = when {
            !signed -> "$"
            amount.signum() >= 0 -> "+$"
            else -> "-$"
        }
        return "$prefix$numberPart"
    }
}
