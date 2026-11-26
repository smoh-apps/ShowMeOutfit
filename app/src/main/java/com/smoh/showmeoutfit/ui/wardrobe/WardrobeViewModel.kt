package com.smoh.showmeoutfit.ui.wardrobe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.domain.repository.WardrobeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** Single source of truth for closet contents and the transient add/edit form. */
class WardrobeViewModel(
    private val wardrobeRepository: WardrobeRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(WardrobeUiState())
    val state: StateFlow<WardrobeUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<WardrobeEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<WardrobeEffect> = _effects.asSharedFlow()

    private var observeJob: Job? = null
    private val writeMutex = Mutex()

    init {
        observeItems()
    }

    fun onIntent(intent: WardrobeIntent) {
        when (intent) {
            is WardrobeIntent.SelectFilter -> selectFilter(intent.filter)
            is WardrobeIntent.OpenAdd -> openAdd(intent.category, intent.defaultName)
            is WardrobeIntent.OpenEdit -> openEdit(intent.item, intent.defaultName)
            is WardrobeIntent.UpdateEditorCategory ->
                updateEditorCategory(intent.category, intent.defaultName)
            is WardrobeIntent.UpdateEditorGarment ->
                updateEditorGarment(intent.garmentId, intent.defaultName)
            is WardrobeIntent.UpdateEditorFit -> updateEditorFit(intent.fit, intent.defaultName)
            is WardrobeIntent.UpdateEditorName -> updateEditor { editor ->
                editor.copy(name = intent.name, hasCustomName = intent.name.isNotBlank())
            }
            is WardrobeIntent.UpdateEditorWaterResistant -> updateEditor { editor ->
                editor.copy(isWaterResistant = intent.isWaterResistant)
            }
            WardrobeIntent.SaveEditor -> saveEditor()
            WardrobeIntent.DeleteEditor -> deleteEditor()
            WardrobeIntent.DismissEditor -> dismissEditor()
            WardrobeIntent.RetryLoad -> observeItems()
        }
    }

    private fun selectFilter(filter: WardrobeFilter) {
        if (_state.value.selectedFilter == filter) return
        // Clear the previous category atomically with the selection so its items can never flash
        // under the newly selected chip while the replacement repository flow starts.
        _state.update {
            it.copy(
                selectedFilter = filter,
                items = emptyList(),
                isLoading = true,
                loadFailed = false
            )
        }
        observeItems()
    }

    private fun observeItems() {
        observeJob?.cancel()
        val filter = _state.value.selectedFilter
        _state.update {
            it.copy(isLoading = true, loadFailed = false, items = emptyList())
        }
        observeJob = viewModelScope.launch {
            val source = filter.category
                ?.let(wardrobeRepository::observeByCategory)
                ?: wardrobeRepository.observeAll()
            source.catch { error ->
                if (error is CancellationException) throw error
                _state.update { it.copy(isLoading = false, loadFailed = true) }
                _effects.tryEmit(WardrobeEffect.ShowError(WardrobeError.LOAD_FAILED))
            }.collect { items ->
                _state.update { it.copy(items = items, isLoading = false, loadFailed = false) }
            }
        }
    }

    private fun openAdd(category: LayerCategory, defaultName: String) {
        val garmentId = WardrobeCatalog.garmentsFor(category).first()
        _state.update {
            it.copy(
                editor = WardrobeEditorState(
                    category = category,
                    garmentId = garmentId,
                    defaultName = defaultName
                )
            )
        }
    }

    private fun openEdit(item: WardrobeItem, defaultName: String) {
        val garmentId = item.garmentId.takeIf { WardrobeCatalog.categoryFor(it) == item.category }
            ?: WardrobeCatalog.garmentsFor(item.category).first()
        _state.update {
            it.copy(
                editor = WardrobeEditorState(
                    existingItem = item,
                    category = item.category,
                    garmentId = garmentId,
                    fit = item.fit,
                    name = item.name,
                    defaultName = defaultName,
                    hasCustomName = item.name.trim() != defaultName.trim(),
                    isWaterResistant = item.isWaterResistant
                )
            )
        }
    }

    private fun updateEditorCategory(category: LayerCategory, defaultName: String) {
        updateEditor { editor ->
            val garments = WardrobeCatalog.garmentsFor(category)
            val garmentId = editor.garmentId.takeIf(garments::contains) ?: garments.first()
            editor.copy(
                category = category,
                garmentId = garmentId,
                defaultName = defaultName,
                name = if (editor.hasCustomName) editor.name else defaultName
            )
        }
    }

    private fun updateEditorGarment(garmentId: GarmentId, defaultName: String) {
        updateEditor { editor ->
            // A stale selector callback must never persist a garment from another category.
            if (WardrobeCatalog.categoryFor(garmentId) != editor.category) {
                editor
            } else {
                editor.copy(
                    garmentId = garmentId,
                    defaultName = defaultName,
                    name = if (editor.hasCustomName) editor.name else defaultName
                )
            }
        }
    }

    private fun updateEditorFit(fit: GarmentFit, defaultName: String) {
        updateEditor { editor ->
            editor.copy(
                fit = fit,
                defaultName = defaultName,
                name = if (editor.hasCustomName) editor.name else defaultName
            )
        }
    }

    private fun updateEditor(transform: (WardrobeEditorState) -> WardrobeEditorState) {
        _state.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.isSaving) state else state.copy(editor = transform(editor))
        }
    }

    private fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (editor.isSaving) return
        val name = editor.resolvedName ?: return

        viewModelScope.launch {
            writeMutex.withLock {
                _state.update { state ->
                    state.copy(editor = state.editor?.copy(isSaving = true))
                }
                val item = editor.existingItem?.copy(
                    name = name,
                    category = editor.category,
                    garmentId = editor.garmentId,
                    fit = editor.fit,
                    isWaterResistant = editor.isWaterResistant
                ) ?: WardrobeItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    category = editor.category,
                    garmentId = editor.garmentId,
                    fit = editor.fit,
                    isWaterResistant = editor.isWaterResistant,
                    photoRelativePath = null,
                    createdAtEpochMillis = System.currentTimeMillis()
                )

                try {
                    withContext(ioDispatcher) { wardrobeRepository.upsert(item) }
                    _state.update { it.copy(editor = null) }
                    _effects.tryEmit(WardrobeEffect.ItemSaved)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _state.update { state ->
                        state.copy(editor = state.editor?.copy(isSaving = false))
                    }
                    _effects.tryEmit(WardrobeEffect.ShowError(WardrobeError.SAVE_FAILED))
                }
            }
        }
    }

    private fun deleteEditor() {
        val editor = _state.value.editor ?: return
        val id = editor.existingItem?.id ?: return
        if (editor.isSaving) return

        viewModelScope.launch {
            writeMutex.withLock {
                _state.update { state ->
                    state.copy(editor = state.editor?.copy(isSaving = true))
                }
                try {
                    withContext(ioDispatcher) { wardrobeRepository.delete(id) }
                    _state.update { it.copy(editor = null) }
                    _effects.tryEmit(WardrobeEffect.ItemDeleted)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    _state.update { state ->
                        state.copy(editor = state.editor?.copy(isSaving = false))
                    }
                    _effects.tryEmit(WardrobeEffect.ShowError(WardrobeError.DELETE_FAILED))
                }
            }
        }
    }

    private fun dismissEditor() {
        if (_state.value.editor?.isSaving == true) return
        _state.update { it.copy(editor = null) }
    }
}
