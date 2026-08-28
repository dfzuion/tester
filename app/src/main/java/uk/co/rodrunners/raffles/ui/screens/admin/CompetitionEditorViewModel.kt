package uk.co.rodrunners.raffles.ui.screens.admin

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.AppError
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.data.model.Bundle
import uk.co.rodrunners.raffles.data.model.CompetitionDraft
import uk.co.rodrunners.raffles.data.repository.AdminRepository

data class EditorState(
    val draft: CompetitionDraft = CompetitionDraft(),
    val competitionId: String? = null,
    val entriesSold: Int = 0,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val uploadingImage: Boolean = false,
    val savedId: String? = null,
    val error: AppError? = null,
) {
    /** Once entries are sold the commercial terms are locked, server-side too. */
    val structuralLocked: Boolean get() = entriesSold > 0
}

@HiltViewModel
class CompetitionEditorViewModel @Inject constructor(
    private val admin: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /**
     * Called with the route argument; null id means a brand new raffle.
     *
     * This used to return early for null, which left whatever the last raffle
     * had put in state - including its competitionId. Opening "New raffle" a
     * second time therefore showed the previous raffle and saving it called
     * update on that one, so only the first raffle could ever be created.
     * Tracking what we last initialised for makes a null argument reset.
     */
    private var initialised = false
    private var initialisedFor: String? = null

    fun start(competitionId: String?) {
        if (initialised && initialisedFor == competitionId) return
        initialised = true
        initialisedFor = competitionId
        if (competitionId == null) {
            _state.value = EditorState()
            return
        }
        if (_state.value.competitionId == competitionId) return
        _state.update { it.copy(loading = true, competitionId = competitionId) }
        viewModelScope.launch {
            runCatching { admin.manageableCompetitions().first { it.id == competitionId } }
                .onSuccess { c ->
                    _state.update {
                        it.copy(draft = CompetitionDraft.from(c), entriesSold = c.entriesSold, loading = false)
                    }
                }
                .onFailure { t -> _state.update { it.copy(error = Errors.from(t), loading = false) } }
        }
    }

    fun edit(block: (CompetitionDraft) -> CompetitionDraft) =
        _state.update { it.copy(draft = block(it.draft), error = null) }

    /** Picked a photo on the phone: upload it and drop the URL into the draft. */
    fun uploadHeroImage(uri: Uri) {
        _state.update { it.copy(uploadingImage = true, error = null) }
        viewModelScope.launch {
            runCatching { admin.uploadCompetitionImage(uri) }
                .onSuccess { url ->
                    _state.update {
                        it.copy(uploadingImage = false, draft = it.draft.copy(heroImageUrl = url))
                    }
                }
                .onFailure { t -> _state.update { it.copy(uploadingImage = false, error = Errors.from(t)) } }
        }
    }

    fun addBundle() = edit { d ->
        val q = if (d.bundles.isEmpty()) 5 else d.bundles.last().quantity * 2
        d.copy(bundles = d.bundles + Bundle(quantity = q, pricePence = q * d.entryPricePence))
    }

    fun removeBundle(index: Int) = edit { d ->
        d.copy(bundles = d.bundles.filterIndexed { i, _ -> i != index })
    }

    fun editBundle(index: Int, block: (Bundle) -> Bundle) = edit { d ->
        d.copy(bundles = d.bundles.mapIndexed { i, b -> if (i == index) block(b) else b })
    }

    fun save() {
        val s = _state.value
        if (!s.draft.isComplete || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val id = s.competitionId
                if (id == null) {
                    val newId = admin.createCompetition(s.draft)
                    _state.update { it.copy(saving = false, savedId = newId, competitionId = newId) }
                } else {
                    admin.updateCompetition(id, s.draft)
                    _state.update { it.copy(saving = false, savedId = id) }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false, error = Errors.from(t)) }
            }
        }
    }

    fun consumeSaved() = _state.update { it.copy(savedId = null) }
}
