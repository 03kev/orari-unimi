package app.orariunimi

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    entries: List<AppNotificationEntry>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: (AppNotificationEntry) -> Unit,
    onClear: () -> Unit,
    onOpen: (AppNotificationEntry) -> Unit
) {
    val pullState = rememberPullToRefreshState()
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Cancellare tutte le notifiche?") },
        text = { Text("Lo storico delle notifiche verrà eliminato da questo dispositivo.") },
        confirmButton = {
            TextButton(onClick = { confirmClear = false; onClear() }) { Text("Cancella tutte") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
    )
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        if (entries.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(Modifier.offset(y = (-72).dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(72.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null,
                                        modifier = Modifier.size(34.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("Nessuna notifica", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text("Le variazioni, i promemoria e gli aggiornamenti che attivi compariranno qui.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                item(key = "clear-notifications") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                                modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Cancella tutte")
                        }
                    }
                }
                itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                    val section = dayLabel(entry.timestampMillis)
                    val previousSection = entries.getOrNull(index - 1)?.let { dayLabel(it.timestampMillis) }
                    Column {
                        if (section != previousSection) {
                            Text(section.uppercase(Locale.ITALIAN), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp,
                                    top = if (index == 0) 0.dp else 12.dp, bottom = 7.dp))
                        }
                        NotificationSwipeRow(entry, onDelete, onOpen)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSwipeRow(
    entry: AppNotificationEntry,
    onDelete: (AppNotificationEntry) -> Unit,
    onOpen: (AppNotificationEntry) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var offsetX by remember(entry.id) { mutableFloatStateOf(0f) }
    var settleJob by remember(entry.id) { mutableStateOf<Job?>(null) }
    var thresholdReached by remember(entry.id) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val rowWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val deleteThreshold = rowWidth * 0.38f
        val swipeProgress = (-offsetX / deleteThreshold).coerceIn(0f, 1f)

        Box(
            Modifier.matchParentSize().background(
                if (thresholdReached) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(20.dp)).padding(end = 22.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Elimina notifica",
                modifier = Modifier.graphicsLayer {
                    alpha = 0.5f + swipeProgress * 0.5f
                    scaleX = 0.78f + swipeProgress * 0.22f
                    scaleY = scaleX
                },
                tint = if (thresholdReached) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onErrorContainer)
        }
        Card(
            modifier = Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(entry.id, rowWidth) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            thresholdReached = offsetX <= -deleteThreshold
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-rowWidth, 0f)
                            val reachedNow = offsetX <= -deleteThreshold
                            if (reachedNow && !thresholdReached) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            thresholdReached = reachedNow
                        },
                        onDragCancel = {
                            settleJob = scope.launch {
                                animate(offsetX, 0f, animationSpec = spring()) { value, _ -> offsetX = value }
                                thresholdReached = false
                            }
                        },
                        onDragEnd = {
                            settleJob = scope.launch {
                                if (thresholdReached) {
                                    animate(offsetX, -rowWidth, animationSpec = spring()) { value, _ -> offsetX = value }
                                    onDelete(entry)
                                } else {
                                    animate(offsetX, 0f, animationSpec = spring()) { value, _ -> offsetX = value }
                                    thresholdReached = false
                                }
                            }
                        }
                    )
                }
                .clickable { onOpen(entry) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(13.dp), color = entryColor(entry.type),
                    modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(entryIcon(entry.type), contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(timeLabel(entry.timestampMillis), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(entry.message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun entryColor(type: AppNotificationType): Color = when (type) {
    AppNotificationType.IMPORTANT_CHANGE -> MaterialTheme.colorScheme.errorContainer
    AppNotificationType.LESSON_REMINDER -> MaterialTheme.colorScheme.primaryContainer
    AppNotificationType.APP_UPDATE -> MaterialTheme.colorScheme.tertiaryContainer
}

private fun entryIcon(type: AppNotificationType) = when (type) {
    AppNotificationType.IMPORTANT_CHANGE -> Icons.Outlined.NotificationsActive
    AppNotificationType.LESSON_REMINDER -> Icons.Outlined.Schedule
    AppNotificationType.APP_UPDATE -> Icons.Outlined.SystemUpdate
}

private val italianDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val italianTime = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

private fun notificationDate(timestamp: Long) = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())

private fun dayLabel(timestamp: Long): String {
    val date = notificationDate(timestamp).toLocalDate()
    return when (date) {
        LocalDate.now() -> "Oggi"
        LocalDate.now().minusDays(1) -> "Ieri"
        else -> date.format(italianDate)
    }
}

private fun timeLabel(timestamp: Long) = notificationDate(timestamp).format(italianTime)
