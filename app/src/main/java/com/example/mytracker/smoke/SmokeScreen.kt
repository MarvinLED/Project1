package com.example.mytracker.smoke

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mytracker.core.ui.ConfirmDeleteDialog
import com.example.mytracker.core.util.DateUtils
import com.example.mytracker.ui.theme.AppDomain
import com.example.mytracker.ui.theme.statusColor
import com.example.mytracker.ui.theme.topAppBarColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Smoken, one day at a time: what was smoked on the selected day, and how that day and its week
 * stand against the limits set under Ziele.
 *
 * A day at a time rather than one long history, like the Flüssigkeiten screen and unlike Blutdruck:
 * this is counted data, and the number that matters — "wie oft heute" — only means something inside
 * a day. The Verlauf across days is the Analyse screen's job; both series are registered there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmokeScreen(
    onOpenDrawer: () -> Unit,
    onOpenGoals: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmokeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = AppDomain.SMOKE.topAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menü")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::goToPreviousDay) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Vorheriger Tag")
                        }
                        Text(
                            DateUtils.localDateOfEpochDay(uiState.epochDay).format(dateFormatter),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = viewModel::goToNextDay) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Nächster Tag")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = viewModel::startAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Session hinzufügen", modifier = Modifier.padding(start = 8.dp))
            }

            GoalCard(uiState = uiState, onOpenGoals = onOpenGoals)

            SessionsCard(
                uiState = uiState,
                onEdit = viewModel::startEdit,
                onDelete = viewModel::delete,
            )
        }
    }

    uiState.draft?.let { draft ->
        SmokeSessionDialog(
            draft = draft,
            epochDay = uiState.epochDay,
            onTimeChange = viewModel::onTimeChange,
            onPuffsChange = viewModel::onPuffsChange,
            onCbdChange = viewModel::onCbdChange,
            onRatingDuringChange = viewModel::onRatingDuringChange,
            onRatingAfterChange = viewModel::onRatingAfterChange,
            onConfirm = viewModel::saveDraft,
            onDismiss = viewModel::dismissDraft,
        )
    }
}

/**
 * The limits with the day's and the week's counts against them. With none set the card still shows,
 * saying where they are set — an empty screen would leave the feature undiscoverable, and the
 * limits are half of what this category is for.
 */
@Composable
private fun GoalCard(uiState: SmokeUiState, onOpenGoals: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ziele", style = MaterialTheme.typography.titleSmall)

            if (uiState.goals.isEmpty) {
                Text(
                    "Noch kein Limit gesetzt. Unter Ziele → Smoken lässt sich festlegen, wie viele " +
                        "Sessions und Züge es am Tag und in der Woche höchstens sein sollen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenGoals) { Text("Ziele öffnen") }
            } else {
                uiState.goalStatuses.forEach { status -> GoalStatusRow(status) }
            }
        }
    }
}

/**
 * One limit. Green means still inside it — the reverse of most goals in this app, where green is a
 * number that has been reached. The bar fills as the allowance is used up, so a full bar is the bad
 * end here; the numbers beside it say which is which, so the colour is never the only signal.
 */
@Composable
private fun GoalStatusRow(status: SmokeGoalStatus) {
    val color = statusColor(status.isMet)
    val description = if (status.isMet) "Im Limit" else "Limit überschritten"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(status.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                status.valueText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LimitBar(fraction = status.fraction, color = color, statusDescription = description)
    }
}

@Composable
private fun LimitBar(fraction: Float, color: Color, statusDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // Spoken instead of nothing at all: the fill's colour is what says in or over the
            // limit, and a screen reader sees no colours.
            .semantics { contentDescription = statusDescription },
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

/** The day's sessions in the order they happened. Tapping one opens it for correction. */
@Composable
private fun SessionsCard(
    uiState: SmokeUiState,
    onEdit: (SmokeSession) -> Unit,
    onDelete: (SmokeSession) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<SmokeSession?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(daySummary(uiState), style = MaterialTheme.typography.titleSmall)

            if (uiState.sessions.isEmpty()) {
                Text(
                    "An diesem Tag ist nichts eingetragen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            uiState.sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        session.summaryLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f).clickable { onEdit(session) },
                    )
                    IconButton(onClick = { pendingDelete = session }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }
    }

    pendingDelete?.let { session ->
        ConfirmDeleteDialog(
            title = "Session löschen?",
            text = session.summaryLabel(),
            onConfirm = { onDelete(session) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * "3 Sessions · 24 Züge" — the day in one line, with the Züge left off entirely when none were
 * counted rather than shown as a zero that would read as "keine genommen".
 */
private fun daySummary(uiState: SmokeUiState): String {
    val sessions = uiState.sessions.size
    val sessionPart = if (sessions == 1) "1 Session" else "$sessions Sessions"
    val puffs = uiState.dayPuffs
    val uncounted = uiState.daySessionsWithoutPuffs
    return when {
        puffs == 0 -> sessionPart
        uncounted > 0 -> "$sessionPart · mind. $puffs Züge"
        else -> "$sessionPart · $puffs Züge"
    }
}
