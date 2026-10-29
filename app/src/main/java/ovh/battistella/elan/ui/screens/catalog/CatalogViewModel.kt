package ovh.battistella.elan.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ovh.battistella.elan.data.settings.SettingsRepository
import ovh.battistella.elan.domain.Profile
import ovh.battistella.elan.domain.RecoProfile
import ovh.battistella.elan.domain.toRecoProfile
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val profile: StateFlow<RecoProfile> = settings.settings
        .map { it.profile.toRecoProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile().toRecoProfile())
}
