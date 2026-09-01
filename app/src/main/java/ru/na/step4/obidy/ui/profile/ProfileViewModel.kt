package ru.na.step4.obidy.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import ru.na.step4.obidy.data.profile.ProfileRu
import ru.na.step4.obidy.data.profile.ProfileSnapshot
import ru.na.step4.obidy.data.profile.ProfileStore

class ProfileViewModel(
    private val store: ProfileStore,
    private val canCollect: () -> Boolean = { false },
    private val onLanguageChanged: (String) -> Unit = {}
) : ViewModel() {
    val snapshot: StateFlow<ProfileSnapshot> = store.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), store.current)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun save(
        name: String,
        birthYear: String,
        location: String,
        aboutMe: String,
        program: String,
        problems: Set<String>,
        personality: String,
        answers: Map<String, String>
    ) {
        store.applyAll(
            name = name,
            birthYear = birthYear,
            location = location,
            aboutMe = aboutMe,
            program = program,
            problems = problems,
            personality = personality,
            answers = answers
        )
        _notice.value = ProfileRu.saved
    }

    fun setLanguage(code: String) {
        store.languageCode = code
        onLanguageChanged(store.languageCode)
        _notice.value = ProfileRu.saved
    }

    fun setPersonalityEnabled(value: Boolean) {
        store.personalityEnabled = value
    }

    fun setPersonalityCollect(value: Boolean) {
        if (value && !canCollect()) {
            _notice.value = ProfileRu.collectProNeeded
            return
        }
        store.personalityCollectEnabled = value
    }

    fun clearNotice() {
        _notice.value = null
    }

    companion object {
        fun factory(
            store: ProfileStore,
            canCollect: () -> Boolean = { false },
            onLanguageChanged: (String) -> Unit = {}
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(store, canCollect, onLanguageChanged) as T
            }
        }
    }
}
