package f.cking.software.data.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.location.LocationListenerCompat
import f.cking.software.data.repo.SettingsRepository
import f.cking.software.domain.interactor.SaveReportInteractor
import f.cking.software.domain.model.JournalEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.lang.Runnable
import java.util.function.Consumer

class LocationProvider(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val saveReportInteractor: SaveReportInteractor,
) {

    private val TAG = "LocationProvider"

    private val locationState = MutableStateFlow<LocationHandle?>(null)

    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val consumer = Consumer<Location?> {
        val provider = provider()
        if (it != null) {
            Log.d(
                TAG,
                "New location: lat=${it.latitude}, lng=${it.longitude} (provider: $provider)"
            )
            locationState.tryEmit(LocationHandle(it, System.currentTimeMillis()))
        } else {
            Log.d(TAG, "Empty location emitted  (provider: $provider)")
        }
        scheduleNextRequest()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val locationListener = LocationListenerCompat {
        consumer.accept(it)
    }

    private var isActive: Boolean = false
    private var cancellationSignal: CancellationSignal = CancellationSignal()

    private val handler = Handler(Looper.getMainLooper())
    private val nextLocationRequest = Runnable {
        try {
            fetchLocation()
        } catch (error: Throwable) {
            reportError(error)
            scheduleNextRequest()
        }
    }

    fun isLocationAvailable(): Boolean {
        return (locationManager?.isProviderEnabled(provider())
            ?: false) && (locationManager?.isLocationEnabled ?: false)
    }

    fun isActive(): Boolean {
        return isActive
    }

    fun observeLocation(): Flow<LocationHandle?> {
        return locationState
    }

    suspend fun getFreshLocation(): Location? {
        return observeLocation()
            .firstOrNull()
            ?.takeIf { it.isFresh() }
            ?.location
    }

    @SuppressLint("MissingPermission")
    fun startLocationFetching() {
        if (!isLocationAvailable()) {
            throw LocationManagerIsNotAvailableException()
        }
        fetchLocation()
        isActive = true
    }

    fun stopLocationListening() {
        locationManager?.removeUpdates(locationListener)
        cancellationSignal.cancel()
        handler.removeCallbacks(nextLocationRequest)
        isActive = false
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!cancellationSignal.isCanceled) {
            cancellationSignal.cancel()
        }
        cancellationSignal = CancellationSignal()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            locationManager?.getCurrentLocation(
                provider(),
                LocationRequest.Builder(INTERVAL_MS)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build(),
                cancellationSignal,
                context.mainExecutor,
                consumer
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager?.getCurrentLocation(
                provider(),
                cancellationSignal,
                context.mainExecutor,
                consumer
            )
        } else {
            locationManager?.requestSingleUpdate(
                provider(),
                locationListener,
                context.mainLooper,
            )
        }
    }

    private fun provider(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !settingsRepository.getUseGpsLocationOnly()) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }
    }

    private fun scheduleNextRequest() {
        handler.postDelayed(nextLocationRequest, INTERVAL_MS)
    }

    private fun reportError(error: Throwable) {
        Log.e(TAG, error.message.orEmpty(), error)
        scope.launch {
            val report = JournalEntry.Report.Error(
                error.message ?: error::class.java.name,
                error.stackTraceToString()
            )
            saveReportInteractor.execute(report)
        }
    }

    private fun LocationHandle.isFresh(): Boolean {
        return System.currentTimeMillis() - this.emitTime < ALLOWED_LOCATION_LIVETIME_MS
    }

    data class LocationHandle(
        val location: Location,
        val emitTime: Long,
    )

    class LocationManagerIsNotAvailableException :
        IllegalStateException("Location is not available or turned off")

    companion object {
        private const val INTERVAL_MS = 10_000L
        private const val ALLOWED_LOCATION_LIVETIME_MS = 2L * 60L * 1000L // 2 min
    }
}