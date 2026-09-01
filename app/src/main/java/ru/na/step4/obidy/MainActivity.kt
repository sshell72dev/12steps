package ru.na.step4.obidy

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ru.na.step4.obidy.ui.AppLockGate
import ru.na.step4.obidy.ui.Step4Nav
import ru.na.step4.obidy.ui.theme.Step4Theme
import ru.na.steps12.voice.ui.VoiceHost

class MainActivity : FragmentActivity() {
    private val appLockStore by lazy { (application as Step4App).appLockStore }
    private var unlocked by mutableStateOf(false)
    private var biometricPromptActive = false
    private var suppressLockUntil = 0L
    /** Bumped when returning from YooKassa deep link so paywall can refresh. */
    var premiumReturnTick by mutableStateOf(0)
        private set

    /**
     * Lock only when the whole app goes to background — not when the system
     * biometric dialog covers this activity. Xiaomi reports that overlay as
     * process onStop, which would otherwise cancel fingerprint and re-lock.
     */
    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (biometricPromptActive) return
            if (SystemClock.elapsedRealtime() < suppressLockUntil) return
            if (appLockStore.isConfigured) {
                unlocked = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        handlePremiumReturn(intent)
        handleMessengerInvite(intent)
        enableEdgeToEdge()
        setContent {
            Step4Theme {
                VoiceHost(plugin = (application as Step4App).voicePlugin) {
                    Surface(modifier = Modifier.fillMaxSize().imePadding()) {
                        AppLockGate(
                            activity = this@MainActivity,
                            store = appLockStore,
                            unlocked = unlocked,
                            onBiometricPromptActive = { biometricPromptActive = it },
                            onUnlocked = {
                                suppressLockUntil = SystemClock.elapsedRealtime() + 2_000
                                unlocked = true
                            }
                        ) {
                            Step4Nav()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePremiumReturn(intent)
        handleMessengerInvite(intent)
    }

    private fun handlePremiumReturn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "ru.na.steps12" && data.host == "premium") {
            premiumReturnTick += 1
            // Returning from browser payment should not immediately re-lock.
            suppressLockUntil = SystemClock.elapsedRealtime() + 5_000
        }
    }

    private fun handleMessengerInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "ru.na.steps12" && data.host == "messenger") {
            (application as Step4App).messengerRepository.offerInvite(data.toString())
            suppressLockUntil = SystemClock.elapsedRealtime() + 5_000
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        super.onDestroy()
    }
}
