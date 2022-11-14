package f.cking.software.ui.profiledetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import f.cking.software.common.navigation.BackCommand
import f.cking.software.common.navigation.NavRouter
import f.cking.software.data.repo.RadarProfilesRepository
import f.cking.software.domain.model.ManufacturerInfo
import f.cking.software.domain.model.RadarProfile
import kotlinx.coroutines.launch
import java.util.*

class ProfileDetailsViewModel(
    private val profileId: Optional<Int>,
    private val radarProfilesRepository: RadarProfilesRepository,
    val router: NavRouter,
) : ViewModel() {

    var name: String by mutableStateOf("")
    var description: String by mutableStateOf("")
    var isActive: Boolean by mutableStateOf(true)
    var filter: Optional<UiFilterState> by mutableStateOf(Optional.empty())

    init {
        if (profileId.isPresent) {
            viewModelScope.launch {
                val profile = radarProfilesRepository.getById(id = profileId.get())
                loadExisting(profile)
            }
        }
    }

    private fun loadExisting(profile: RadarProfile?) {
        if (profile != null) {
            // TODO
        } else {
            back()
        }
    }

    fun back() {
        router.navigate(BackCommand)
    }

    sealed class UiFilterState {

        class LastDetectionInterval : UiFilterState() {
            var from: Optional<Long> by mutableStateOf(Optional.empty())
            var to: Optional<Long> by mutableStateOf(Optional.empty())
        }

        class FirstDetectionInterval : UiFilterState() {
            var from: Optional<Long> by mutableStateOf(Optional.empty())
            var to: Optional<Long> by mutableStateOf(Optional.empty())
        }

        class Name : UiFilterState() {
            var name: String by mutableStateOf("")
            var ignoreCase: Boolean by mutableStateOf(true)
        }

        class Address : UiFilterState() {
            var address: String by mutableStateOf("")
        }

        class Manufacturer : UiFilterState() {
            var manufacturer: Optional<ManufacturerInfo> by mutableStateOf(Optional.empty())
        }

        class IsFavorite : UiFilterState() {
            var favorite: Boolean by mutableStateOf(false)
        }

        class MinLostTime : UiFilterState() {
            var minLostTime: Optional<Long> by mutableStateOf(Optional.empty())
        }

        class Any : UiFilterState() {
            var filters: List<UiFilterState> by mutableStateOf(emptyList())

            fun delete(filter: UiFilterState) {
                filters = filters.filter { it !== filter }
            }
        }

        class All : UiFilterState() {
            var filters: List<UiFilterState> by mutableStateOf(emptyList())

            fun delete(filter: UiFilterState) {
                filters = filters.filter { it !== filter }
            }
        }

        class Not : UiFilterState() {
            var filter: Optional<UiFilterState> by mutableStateOf(Optional.empty())

            fun delete(filter: UiFilterState) {
                this.filter = Optional.empty()
            }
        }

        class Unknown : UiFilterState()
    }
}