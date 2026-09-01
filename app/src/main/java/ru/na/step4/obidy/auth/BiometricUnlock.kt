package ru.na.step4.obidy.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ru.na.step4.obidy.Ru

object BiometricUnlock {
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        allowedAuthenticators(activity) != 0

    fun statusMessage(activity: FragmentActivity): String? {
        if (canAuthenticate(activity)) return null
        val manager = BiometricManager.from(activity)
        val strong = manager.canAuthenticate(BIOMETRIC_STRONG)
        val weak = manager.canAuthenticate(BIOMETRIC_WEAK)
        return if (strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
            weak == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        ) {
            Ru.lockBiometricNoneEnrolled
        } else {
            Ru.lockBiometricUnavailable
        }
    }

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        val authenticators = allowedAuthenticators(activity)
        if (authenticators == 0) {
            onError(statusMessage(activity) ?: Ru.lockBiometricUnavailable)
            return
        }
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> onDismissed()
                            else -> onError(errString.toString())
                        }
                    }
                }
            )
            val info = buildPromptInfo(authenticators)
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(e.message ?: Ru.lockBiometricUnavailable)
        }
    }

    /**
     * Xiaomi fingerprint is Class 3 (STRONG). Asking for WEAK can skip the
     * sensor and look at face unlock instead — or cancel the dialog.
     */
    private fun allowedAuthenticators(activity: FragmentActivity): Int {
        val manager = BiometricManager.from(activity)
        if (manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
            return BIOMETRIC_STRONG
        }
        if (manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            return BIOMETRIC_WEAK
        }
        return 0
    }

    private fun buildPromptInfo(authenticators: Int): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(Ru.lockBiometricTitle)
            .setSubtitle(Ru.lockBiometricSubtitle)
            .setNegativeButtonText(Ru.cancel)
            .setConfirmationRequired(false)
        return try {
            builder.setAllowedAuthenticators(authenticators).build()
        } catch (_: Exception) {
            builder.build()
        }
    }
}
