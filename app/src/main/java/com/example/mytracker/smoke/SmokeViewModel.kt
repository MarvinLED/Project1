package com.example.mytracker.smoke

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mytracker.core.datastore.UserPreferencesRepository
import com.example.mytracker.core.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A session being written, as it is typed. [id] null means a new one; set means the dialog was
 * opened on an existing session and saving corrects it rather than adding a second.
 *
 * The Züge are held as text, not as an Int: an empty field has to stay tellable from a zero, since
 * "nicht gezählt" and "keinen Zug genommen" are different facts about the session.
 */
data class SmokeDraft(
    val id: String? = null,
    val minuteOfDay: Int,
    val puffsText: String = "",
    val cbd: Boolean = false,
    val ratingDuring: Int? = null,
    val ratingAfter: Int? = null,
) {
    val isEditing: Boolean get() = id != null
}

data class SmokeUiState(
    val epochDay: Long,
    /** The selected day's sessions, earliest first. */
    val sessions: List<SmokeSession> = emptyList(),
    /** The whole calendar week the selected day falls in — what a Wochenziel is counted over. */
    val weekSessions: List<SmokeSession> = emptyList(),
    val goals: SmokeGoals = SmokeGoals(),
    /** Non-null while the add/edit dialog is open. */
    val draft: SmokeDraft? = null,
) {
    val goalStatuses: List<SmokeGoalStatus> get() = smokeGoalStatuses(sessions, weekSessions, goals)

    val dayPuffs: Int get() = sessions.puffTotal()
    val daySessionsWithoutPuffs: Int get() = sessions.sessionsWithoutPuffCount()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmokeViewModel @Inject constructor(
    private val smokeRepository: SmokeRepository,
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val _selectedEpochDay = MutableStateFlow(DateUtils.todayEpochDay())
    val selectedEpochDay: StateFlow<Long> = _selectedEpochDay.asStateFlow()

    private val _draft = MutableStateFlow<SmokeDraft?>(null)

    private val dayData = _selectedEpochDay.flatMapLatest { epochDay ->
        val weekStart = DateUtils.startOfWeekEpochDay(epochDay)
        combine(
            smokeRepository.observeForDay(epochDay),
            smokeRepository.observeInRange(weekStart, weekStart + 6),
        ) { sessions, week -> Triple(epochDay, sessions, week) }
    }

    val uiState: StateFlow<SmokeUiState> = combine(
        dayData,
        userPreferencesRepository.userPreferences.map { it.smokeGoals },
        _draft,
    ) { (epochDay, sessions, week), goals, draft ->
        SmokeUiState(
            epochDay = epochDay,
            sessions = sessions,
            weekSessions = week,
            goals = goals,
            draft = draft,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SmokeUiState(_selectedEpochDay.value),
    )

    fun goToPreviousDay() {
        _selectedEpochDay.value -= 1
    }

    fun goToNextDay() {
        _selectedEpochDay.value += 1
    }

    /**
     * Opens the dialog on a new session. The time starts at the clock only when the selected day is
     * today — on a day being caught up on, "now" is a time that day never had, so it starts at noon
     * and has to be set.
     */
    fun startAdd() {
        val today = DateUtils.todayEpochDay()
        val start = if (_selectedEpochDay.value == today) {
            LocalTime.now().let { it.hour * 60 + it.minute }
        } else {
            12 * 60
        }
        _draft.value = SmokeDraft(minuteOfDay = start)
    }

    fun startEdit(session: SmokeSession) {
        _draft.value = SmokeDraft(
            id = session.id,
            minuteOfDay = session.minuteOfDay,
            puffsText = session.puffs?.toString().orEmpty(),
            cbd = session.cbd,
            ratingDuring = session.ratingDuring,
            ratingAfter = session.ratingAfter,
        )
    }

    fun dismissDraft() {
        _draft.value = null
    }

    private fun editDraft(block: (SmokeDraft) -> SmokeDraft) {
        _draft.value = _draft.value?.let(block)
    }

    fun onTimeChange(minuteOfDay: Int) = editDraft { it.copy(minuteOfDay = minuteOfDay) }

    /** Digits only: the Züge are a count, and a stray letter would silently drop the whole value. */
    fun onPuffsChange(value: String) = editDraft { it.copy(puffsText = value.filter(Char::isDigit)) }

    /** One Zug up or down — see [steppedPuffs]. */
    fun stepPuffs(delta: Int) = editDraft { it.copy(puffsText = steppedPuffs(it.puffsText, delta)) }

    fun onCbdChange(cbd: Boolean) = editDraft { it.copy(cbd = cbd) }

    /** Null takes the rating off again — the sliders can be switched off, see the screen. */
    fun onRatingDuringChange(rating: Int?) = editDraft { it.copy(ratingDuring = rating) }

    fun onRatingAfterChange(rating: Int?) = editDraft { it.copy(ratingAfter = rating) }

    fun saveDraft() {
        val draft = _draft.value ?: return
        val epochDay = _selectedEpochDay.value
        _draft.value = null
        viewModelScope.launch {
            smokeRepository.logSession(
                epochDay = epochDay,
                minuteOfDay = draft.minuteOfDay,
                puffs = draft.puffsText.toIntOrNull(),
                cbd = draft.cbd,
                ratingDuring = draft.ratingDuring,
                ratingAfter = draft.ratingAfter,
                id = draft.id,
            )
        }
    }

    fun delete(session: SmokeSession) {
        viewModelScope.launch { smokeRepository.delete(session) }
    }
}

/**
 * The Züge field one step further, as the − and + beside it move it.
 *
 * Empty is "nicht gezählt", not zero, and the steps keep that reachable in both directions: "+" on
 * an empty field starts at 1, and "−" on a 1 empties it again rather than stopping there. Without
 * that last step the buttons could take the field *into* a count but never back out of one, and
 * "nicht gezählt" would be typeable only with the keyboard.
 */
fun steppedPuffs(current: String, delta: Int): String {
    val next = (current.toIntOrNull() ?: 0) + delta
    return if (next <= 0) "" else next.toString()
}
