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

    /** Bumped when a psychologist reminder notification is tapped. */
    var psychOpenTick by mutableStateOf(0)
        private set

    var pendingPsychOpen by mutableStateOf(false)
        private set

    fun consumePendingPsychOpen(): Boolean {
        if (!pendingPsychOpen) return false
        pendingPsychOpen = false
        return true
    }

    /** Bumped when a system alert notification is tapped (lock screen / shade). */
    var alertsOpenTick by mutableStateOf(0)
        private set

    var pendingAlertsOpen by mutableStateOf(false)
        private set

    var pendingAlertTarget by mutableStateOf("")
        private set

    fun consumePendingAlertsOpen(): Boolean {
        if (!pendingAlertsOpen) return false
        pendingAlertsOpen = false
        pendingAlertTarget = ""
        return true
    }

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
        handleAlertsOpen(intent)
        handlePsychReminder(intent)
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
        handleAlertsOpen(intent)
        handlePsychReminder(intent)
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

    private fun handleAlertsOpen(intent: Intent?) {
        if (intent?.getBooleanExtra(
                ru.na.step4.obidy.data.alerts.AppAlerts.EXTRA_OPEN,
                false
            ) == true
        ) {
            alertsOpenTick += 1
            pendingAlertsOpen = true
            pendingAlertTarget = intent.getStringExtra(
                ru.na.step4.obidy.data.alerts.AppAlerts.EXTRA_TARGET
            ).orEmpty()
            suppressLockUntil = SystemClock.elapsedRealtime() + 5_000
        }
    }

    private fun handlePsychReminder(intent: Intent?) {
        if (intent?.getBooleanExtra(
                ru.na.step4.obidy.data.psych.PsychReminderWorker.EXTRA_OPEN_PSYCH,
                false
            ) == true
        ) {
            psychOpenTick += 1
            pendingPsychOpen = true
            suppressLockUntil = SystemClock.elapsedRealtime() + 5_000
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        super.onDestroy()
    }
}
