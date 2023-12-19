package f.cking.software.ui.selectdevice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import f.cking.software.data.repo.DevicesRepository
import f.cking.software.domain.model.DeviceData
import f.cking.software.utils.navigation.BackCommand
import f.cking.software.utils.navigation.Router
import kotlinx.coroutines.launch

class SelectDeviceViewModel(
    private val router: Router,
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    var devices: List<DeviceData> by mutableStateOf(emptyList())
    var searchStr: String by mutableStateOf("")

    init {
        viewModelScope.launch {
            devices = devicesRepository.getDevices()
        }
    }

    fun back() {
        router.navigate(BackCommand)
    }
}