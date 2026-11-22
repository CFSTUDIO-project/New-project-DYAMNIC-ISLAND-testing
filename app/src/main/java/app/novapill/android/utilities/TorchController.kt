package app.novapill.android.utilities

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TorchController {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var manager: CameraManager? = null
    private var cameraId: String? = null
    private var callbackRegistered = false

    @Synchronized
    fun attach(context: Context) {
        if (manager != null) return
        val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
        manager = cameraManager
        cameraId = runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (!callbackRegistered) {
            runCatching {
                cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) {
                        if (id == cameraId) {
                            _enabled.value = enabled
                            _error.value = null
                        }
                    }
                    override fun onTorchModeUnavailable(id: String) {
                        if (id == cameraId) _error.value = "Flashlight is temporarily unavailable"
                    }
                }, Handler(Looper.getMainLooper()))
                callbackRegistered = true
            }.onFailure { _error.value = it.message ?: "Torch callback unavailable" }
        }
        if (cameraId == null) _error.value = "No flashlight hardware found"
    }

    fun toggle(context: Context) {
        attach(context)
        val id = cameraId
        val cameraManager = manager
        if (id == null || cameraManager == null) {
            _error.value = "This device does not expose a flashlight"
            return
        }
        runCatching { cameraManager.setTorchMode(id, !_enabled.value) }
            .onFailure { _error.value = it.message ?: "Unable to change flashlight state" }
    }
}
