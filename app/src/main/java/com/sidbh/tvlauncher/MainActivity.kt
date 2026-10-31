package com.sidbh.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.sidbh.tvlauncher.ui.LauncherRoot
import com.sidbh.tvlauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val prefs = LauncherPrefs(app)
        state = LauncherState(
            apps = AppRepository(app),
            inputs = InputRepository(app),
            prefs = prefs,
            pinStore = PinStore(app),
            weatherRepo = WeatherRepository(prefs),
            content = ContentRepository(),
            remoteImages = RemoteImages(),
            bluetooth = BluetoothRepository(app),
            scope = lifecycleScope,
        )
        handleExtras(intent)
        setContent {
            // A home screen has nowhere to go back to; overlays register their own handlers on top of this.
            BackHandler(enabled = true) {}
            LauncherTheme {
                LauncherRoot(state)
            }
        }
    }

    // singleTask: pressing Home while we are already showing lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExtras(intent)
        state.resetToHome()
    }

    private fun handleExtras(intent: Intent?) {
        intent?.getStringExtra(EXTRA_CITY)?.let(state::requestCity)
        // `adb shell am start -n com.sidbh.tvlauncher/.MainActivity --es style retro`: classic, glass, liststyle, arcade, retro.
        intent?.getStringExtra(EXTRA_STYLE)?.let { name ->
            HomeStyle.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(state::chooseHomeStyle)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            state.showQuick = true
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private companion object {
        const val EXTRA_CITY = "city"
        const val EXTRA_STYLE = "style"
    }
}
