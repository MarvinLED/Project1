package com.example.mytracker.smoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mytracker.core.ui.TimeOfDayField
import com.example.mytracker.core.util.DateUtils
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The one form a session is written in, for both adding and correcting — the fields are the same
 * either way, so there is no second screen for editing.
 *
 * Only the time is mandatory. Everything else is left blank by default rather than prefilled with a
 * plausible value: a Zugzahl or a rating that was never actually given would be indistinguishable
 * from one that was, and this screen exists to be honest about how much was smoked.
 */
@Composable
fun SmokeSessionDialog(
    draft: SmokeDraft,
    epochDay: Long,
    onTimeChange: (Int) -> Unit,
    onPuffsChange: (String) -> Unit,
    onPuffsStep: (Int) -> Unit,
    onCbdChange: (Boolean) -> Unit,
    onRatingDuringChange: (Int?) -> Unit,
    onRatingAfterChange: (Int?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.isEditing) "Session bearbeiten" else "Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Which day is being written to, spelled out: the dialog is opened from a screen
                // that can be scrolled back, and the time alone would not say which day it is on.
                Text(
                    "Für ${DateUtils.localDateOfEpochDay(epochDay).format(dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                TimeOfDayField(
                    label = "Uhrzeit",
                    value = draft.minuteOfDay,
                    onValueChange = onTimeChange,
                    defaultMinuteOfDay = draft.minuteOfDay,
                )

                // Counting up beats typing: the Züge are usually known as "einer mehr als eben",
                // and reaching for the keyboard mid-session is what stops them being counted at
                // all. The field stays writable for the times a whole number is already known.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = draft.puffsText,
                        onValueChange = onPuffsChange,
                        label = { Text("Züge (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { onPuffsStep(-1) },
                        // Nothing to take away from an uncounted session, and a button that would
                        // do nothing is better greyed than silently ignored.
                        enabled = draft.puffsText.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Ein Zug weniger")
                    }
                    FilledTonalIconButton(onClick = { onPuffsStep(1) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Ein Zug mehr")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("CBD", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = draft.cbd, onCheckedChange = onCbdChange)
                }

                RatingField(
                    label = "Bewertung dabei",
                    value = draft.ratingDuring,
                    onChange = onRatingDuringChange,
                )
                RatingField(
                    label = "Bewertung danach",
                    value = draft.ratingAfter,
                    onChange = onRatingAfterChange,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * A 1–10 rating that can also be absent. The slider only appears once a rating has been asked for,
 * because a slider always sits *somewhere* — one shown by default would be a value nobody chose,
 * and there would be no way left to say "nicht bewertet".
 *
 * It starts in the middle, the one position that claims nothing about the session.
 */
@Composable
private fun RatingField(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (value == null) {
                TextButton(onClick = { onChange((MIN_SMOKE_RATING + MAX_SMOKE_RATING) / 2) }) {
                    Text("Bewerten")
                }
            } else {
                Text("$value/$MAX_SMOKE_RATING", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onChange(null) }) { Text("Entfernen") }
            }
        }
        if (value != null) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = MIN_SMOKE_RATING.toFloat()..MAX_SMOKE_RATING.toFloat(),
                // Stops *between* the ends, so the handle snaps to whole ratings.
                steps = MAX_SMOKE_RATING - MIN_SMOKE_RATING - 1,
            )
        }
    }
}
