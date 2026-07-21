package com.dailyapp.videograbber

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailyapp.videograbber.ui.DownloadViewModel
import com.dailyapp.videograbber.ui.HomeScreen
import com.dailyapp.videograbber.ui.VpnSettingsScreen
import com.dailyapp.videograbber.ui.VpnViewModel

class MainActivity : ComponentActivity() {

    private val downloadViewModel: DownloadViewModel by viewModels()
    private val vpnViewModel: VpnViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val vpnConsentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                vpnViewModel.onConsentGranted()
            } else {
                vpnViewModel.onConsentDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var showVpnSettings by remember { mutableStateOf(false) }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showVpnSettings) {
                        VpnSettingsScreen(
                            viewModel = vpnViewModel,
                            onBack = { showVpnSettings = false },
                            onConnectClicked = {
                                val consentIntent = vpnViewModel.requestConnect(this)
                                if (consentIntent != null) {
                                    vpnConsentLauncher.launch(consentIntent)
                                }
                            }
                        )
                    } else {
                        HomeScreen(
                            viewModel = downloadViewModel,
                            onOpenVpnSettings = { showVpnSettings = true }
                        )
                    }
                }
            }
        }
    }
}
