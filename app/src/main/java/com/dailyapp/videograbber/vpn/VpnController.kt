package com.dailyapp.videograbber.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * Thin wrapper around the official WireGuard tunnel library.
 *
 * This is a personal VPN client only: the user must supply their own WireGuard
 * configuration (their own server, or one issued by a VPN provider they already use).
 * There is no bundled server and no built-in "unblock any site" behavior.
 */
private class SimpleTunnel(private val tunnelName: String) : Tunnel {
    var onStateChange: ((Tunnel.State) -> Unit)? = null
    override fun getName(): String = tunnelName
    override fun onStateChange(newState: Tunnel.State) {
        onStateChange?.invoke(newState)
    }
}

class VpnController(context: Context) {
    private val backend: Backend = GoBackend(context.applicationContext)
    private val tunnel = SimpleTunnel("personal")

    fun setOnStateChange(listener: (Tunnel.State) -> Unit) {
        tunnel.onStateChange = listener
    }

    /** Returns a consent Intent that must be launched if the system hasn't already granted VPN permission. */
    fun prepareIntent(activity: Activity): Intent? = GoBackend.VpnService.prepare(activity)

    fun connect(configText: String) {
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun currentState(): Tunnel.State = try {
        backend.getState(tunnel)
    } catch (e: Exception) {
        Tunnel.State.DOWN
    }
}
