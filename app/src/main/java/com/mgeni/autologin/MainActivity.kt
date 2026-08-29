package com.mgeni.autologin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mgeni.autologin.data.AppLogger
import com.mgeni.autologin.data.NetworkMonitor
import com.mgeni.autologin.data.PortalClient
import com.mgeni.autologin.data.PreferencesManager
import com.mgeni.autologin.ui.MgeniApp
import com.mgeni.autologin.ui.theme.MgeniTheme
import com.mgeni.autologin.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appCtx = applicationContext
                return MainViewModel(
                    preferencesManager = PreferencesManager(appCtx),
                    portalClient = PortalClient(),
                    networkMonitor = NetworkMonitor(appCtx)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AppLogger.init(applicationContext)

        setContent {
            MgeniTheme {
                MgeniApp(viewModel = viewModel)
            }
        }
    }
}
