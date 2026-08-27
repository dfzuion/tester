package uk.co.rodrunners.raffles.ui.screens.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.rodrunners.raffles.core.Errors
import uk.co.rodrunners.raffles.core.UiState
import uk.co.rodrunners.raffles.data.model.LegalDocument
import uk.co.rodrunners.raffles.data.repository.ContentRepository
import uk.co.rodrunners.raffles.ui.components.StateHost
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@HiltViewModel
class LegalViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val content: ContentRepository,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedState["documentId"])

    private val _state = MutableStateFlow<UiState<LegalDocument>>(UiState.Loading)
    val state: StateFlow<UiState<LegalDocument>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val doc = content.legal(documentId)
                _state.value = doc?.let { UiState.Success(it) }
                    ?: UiState.Empty(
                        "Not published yet",
                        "This document hasn't been published. Contact support if you need a copy.",
                    )
            } catch (t: Throwable) {
                _state.value = UiState.Error(Errors.from(t))
            }
        }
    }
}

/** Renders published Firestore copy as-is, with its version. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    onBack: () -> Unit,
    viewModel: LegalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (state as? UiState.Success)?.data?.title ?: "Document",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        StateHost(state = state, modifier = Modifier.padding(padding), onRetry = viewModel::load) { doc ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.gutter),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Version ${doc.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RrrColors.Slate,
                )
                Spacer(Modifier.height(20.dp))
                // The body is stored as plain text with blank-line paragraphs, so it
                // renders exactly as published without the app reformatting anything.
                doc.body.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    val trimmed = paragraph.trim()
                    val heading = trimmed.length < 80 && !trimmed.endsWith(".")
                    Text(
                        trimmed,
                        style = if (heading) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        color = if (heading) RrrColors.Bone else RrrColors.Mist,
                    )
                    Spacer(Modifier.height(if (heading) 8.dp else 18.dp))
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
