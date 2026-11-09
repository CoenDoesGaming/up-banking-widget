package au.com.up.lockwidget.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// Compact Up look: OLED black, coral for balances and accents, yellow only for "pending"/stale
private val Black = Color(0xFF000000)
private val Surface = Color(0xFF1A1A1F)
private val Coral = Color(0xFFFF7A64)
private val Yellow = Color(0xFFFFEE52)
private val Muted = Color(0xFF9A9AA3)

private val UpColors = ColorScheme(
    primary = Coral,
    onPrimary = Black,
    primaryContainer = Surface,
    onPrimaryContainer = Color.White,
    tertiary = Yellow,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Color(0xFF26262C),
    onSurface = Color.White,
    onSurfaceVariant = Muted,
    background = Black,
    onBackground = Color.White
)

private enum class Page { ACTIVITY, JOINT, SAVERS }

@Composable
fun UpWearApp(
    detail: DetailStore.Detail?,
    snapshot: SnapshotStore.Snapshot?,
    syncStatus: SyncStatus
) {
    // Freshest of the two pushes: snapshot arrives every phone sync, detail only when data changes
    val lastSync = maxOf(detail?.syncedAt ?: 0L, snapshot?.updatedAt ?: 0L)
    MaterialTheme(colorScheme = UpColors) {
        AppScaffold(timeText = { TimeText() }) {
            val nav = rememberSwipeDismissableNavController()
            SwipeDismissableNavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomePager(detail, snapshot, lastSync, syncStatus) { id ->
                        nav.navigate("account/$id")
                    }
                }
                composable("account/{id}") { entry ->
                    val account = entry.arguments?.getString("id")?.let { detail?.account(it) }
                    AccountScreen {
                        if (account == null) {
                            item { Text("Saver not found", color = Muted) }
                        } else {
                            accountBody(account, account.name, detail, lastSync, syncStatus)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePager(
    detail: DetailStore.Detail?,
    snapshot: SnapshotStore.Snapshot?,
    lastSync: Long,
    syncStatus: SyncStatus,
    onOpenAccount: (String) -> Unit
) {
    // 2Up only gets a page when the phone found a joint account
    val pages = listOfNotNull(
        Page.ACTIVITY,
        Page.JOINT.takeIf { detail?.joint != null },
        Page.SAVERS
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(state = pagerState) { index ->
            AnimatedPage(pageIndex = index, pagerState = pagerState) {
                when (pages[index]) {
                    Page.ACTIVITY -> AccountScreen {
                        val spending = detail?.spending
                        if (spending != null) {
                            accountBody(spending, "Spending", detail, lastSync, syncStatus)
                        } else {
                            waitingForPhone(snapshot, lastSync, syncStatus)
                        }
                    }
                    Page.JOINT -> AccountScreen {
                        detail?.joint?.let { accountBody(it, "2Up", detail, lastSync, syncStatus) }
                    }
                    Page.SAVERS -> AccountScreen {
                        saversBody(detail, lastSync, syncStatus, onOpenAccount)
                    }
                }
            }
        }
    }
}

/** Scrollable round-screen list with rotary support and the scroll indicator. */
@Composable
private fun AccountScreen(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

private fun ScalingLazyListScope.balanceHeader(label: String, cents: Long) {
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                formatMoney(cents),
                color = Coral,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

private fun ScalingLazyListScope.accountBody(
    account: DetailStore.Account,
    label: String,
    detail: DetailStore.Detail?,
    lastSync: Long,
    syncStatus: SyncStatus
) {
    balanceHeader(label, account.balanceCents)
    if (account.transactions.isEmpty()) {
        item { Text("No recent activity", color = Muted, fontSize = 12.sp) }
    }
    val zone = ZoneId.systemDefault()
    var lastDate: LocalDate? = null
    account.transactions.forEach { tx ->
        val date = tx.createdAt?.atZoneSameInstant(zone)?.toLocalDate()
        if (date != null && date != lastDate) {
            lastDate = date
            item {
                Text(
                    dayLabel(date),
                    modifier = Modifier.padding(top = 6.dp),
                    color = Muted,
                    fontSize = 11.sp
                )
            }
        }
        item { TransactionRow(tx, tx.transferAccountId?.let { detail?.account(it) }) }
    }
    syncFooter(lastSync, syncStatus)
}

private fun ScalingLazyListScope.saversBody(
    detail: DetailStore.Detail?,
    lastSync: Long,
    syncStatus: SyncStatus,
    onOpenAccount: (String) -> Unit
) {
    val savers = detail?.savers.orEmpty()
    balanceHeader("Total saved", savers.sumOf { it.balanceCents })
    if (detail == null) {
        item { Text("Waiting for phone", color = Muted, fontSize = 12.sp) }
    } else if (savers.isEmpty()) {
        item { Text("No savers yet", color = Muted, fontSize = 12.sp) }
    }
    savers.forEach { saver ->
        item {
            Button(
                onClick = { onOpenAccount(saver.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(),
                label = { Text(saver.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    Text(
                        if (saver.joint) "${formatMoney(saver.balanceCents)} · 2Up"
                        else formatMoney(saver.balanceCents),
                        color = Coral
                    )
                }
            )
        }
    }
    syncFooter(lastSync, syncStatus)
}

/** Before the first detail push: fall back to the complication snapshot if there is one. */
private fun ScalingLazyListScope.waitingForPhone(
    snapshot: SnapshotStore.Snapshot?,
    lastSync: Long,
    syncStatus: SyncStatus
) {
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Spending", color = Muted, fontSize = 12.sp)
            Text(
                snapshot?.spendingBalance?.takeIf { it != "--" } ?: "--",
                color = Coral,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
    item {
        Text(
            "Open the Up widget app on your phone and tap Save & Sync.",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
    syncFooter(lastSync, syncStatus)
}

@Composable
private fun TransactionRow(tx: DetailStore.Tx, transferAccount: DetailStore.Account?) {
    val incoming = tx.cents > 0
    // Transfers between your own accounts: name the account (with its emoji) instead of
    // repeating "Transfer to Holiday", which truncates on a round screen.
    val title = transferAccount?.name ?: tx.description
    val subtitle = when {
        tx.held -> "Pending"
        transferAccount != null -> if (incoming) "Transfer from" else "Transfer to"
        else -> tx.message
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                formatAmount(tx.cents),
                fontSize = 13.sp,
                color = if (incoming) Coral else Color.White,
                maxLines = 1
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (tx.held) Yellow else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ScalingLazyListScope.syncFooter(lastSync: Long, status: SyncStatus) {
    item { SyncFooter(lastSync, status) }
}

@Composable
private fun SyncFooter(lastSync: Long, status: SyncStatus) {
    // Re-render every 30s so "x min ago" keeps moving while the app is open
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val ago = if (lastSync > 0L) "Synced ${relative(now - lastSync)}" else "Not synced yet"
    val text = when (status) {
        SyncStatus.REFRESHING -> "Refreshing… · $ago"
        SyncStatus.NO_PHONE -> "Phone not connected · $ago"
        SyncStatus.IDLE -> ago
    }
    val stale = lastSync == 0L || now - lastSync > 60 * 60_000L
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        fontSize = 10.sp,
        color = if (stale) Yellow else Muted,
        textAlign = TextAlign.Center
    )
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}

private fun relative(ms: Long): String {
    val min = ms / 60_000L
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        else -> "${min / (24 * 60)} d ago"
    }
}

private val moneyFormat = NumberFormat.getNumberInstance(Locale("en", "AU")).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    return "$sign$${moneyFormat.format(abs(cents) / 100.0)}"
}

/** Spending shows plain like the Up app; money coming in gets a "+". */
private fun formatAmount(cents: Long): String =
    if (cents > 0) "+${formatMoney(cents)}" else formatMoney(abs(cents))
