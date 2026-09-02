package br.com.kindlelib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.kindlelib.model.Screen
import br.com.kindlelib.ui.AppViewModel
import br.com.kindlelib.ui.DetailScreen
import br.com.kindlelib.ui.KindleScreen
import br.com.kindlelib.ui.LibraryScreen
import br.com.kindlelib.ui.OnboardingScreen
import br.com.kindlelib.ui.SettingsScreen
import br.com.kindlelib.ui.theme.KindleLibTheme

class MainActivity : ComponentActivity() {

    private var vmRef: AppViewModel? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> vmRef?.onUsbEvent(true)
                UsbManager.ACTION_USB_DEVICE_DETACHED -> vmRef?.onUsbEvent(false)
                Intent.ACTION_MEDIA_MOUNTED -> vmRef?.onUsbEvent(true)
                Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT -> vmRef?.onUsbEvent(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KindleLibTheme {
                val vm: AppViewModel = viewModel()
                vmRef = vm
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val f = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(usbReceiver, f)
        }
        vmRef?.onStart(applicationContext)
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(usbReceiver) }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val screen by vm.screen.collectAsState(initial = Screen.Library)
    val message by vm.message.collectAsState(initial = null)
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            snackbarHost.showSnackbar(m)
            vm.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            is Screen.Onboarding -> OnboardingScreen(vm)
            is Screen.Library -> LibraryScreen(vm)
            is Screen.Detail -> DetailScreen(vm)
            is Screen.Kindle -> KindleScreen(vm)
            is Screen.Settings -> SettingsScreen(vm)
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter))
    }
}
