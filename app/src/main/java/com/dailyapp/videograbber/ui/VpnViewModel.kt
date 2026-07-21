package com.dailyapp.videograbber.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailyapp.videograbber.vpn.VpnConfigStore
import com.dailyapp.videograbber.vpn.VpnController
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages a personal WireGuard tunnel using a config the user supplies themselves
 * (their own server, or one issued by a VPN provider they already use).
 */
class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = VpnController(application)
    private val configStore = VpnConfigStore(application)

    private val _configText = MutableStateFlow(configStore.load())
    val configText: StateFlow<String> = _configText.asStateFlow()

    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var pendingConfig: String? = null

    init {
        controller.setOnStateChange { newState -> _state.value = newState }
        _state.value = controller.currentState()
    }

    fun onConfigChanged(text: String) {
        _configText.value = text
        configStore.save(text)
    }

    /** Returns a system consent Intent to launch if VPN permission hasn't been granted yet, else connects directly. */
    fun requestConnect(activity: Activity): Intent? {
        _error.value = null
        if (_configText.value.isBlank()) {
            _error.value = "먼저 본인 소유의 WireGuard 설정을 붙여넣어 주세요."
            return null
        }
        val consent = controller.prepareIntent(activity)
        if (consent != null) {
            pendingConfig = _configText.value
            return consent
        }
        connectNow(_configText.value)
        return null
    }

    fun onConsentGranted() {
        val config = pendingConfig ?: _configText.value
        pendingConfig = null
        connectNow(config)
    }

    fun onConsentDenied() {
        pendingConfig = null
        _error.value = "VPN 연결 권한이 거부되었습니다."
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                controller.disconnect()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun connectNow(config: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                controller.connect(config)
            } catch (e: Exception) {
                _error.value = "설정을 확인해 주세요: ${e.message}"
            }
        }
    }
}
