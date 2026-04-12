package com.vtbvita.widget

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricHelper {

    private val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** Проверяет, доступна ли биометрия на устройстве (сенсор зарегистрирован). */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Показывает системный биометрический промпт.
     * [onSuccess] — пользователь прошёл проверку.
     * [onCancel]  — нажал «Использовать PIN» или отменил (не ошибка).
     * [onError]   — техническая ошибка (опционально).
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onCancel: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                        else -> onError(errString.toString())
                    }
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("VTB Vita")
            .setSubtitle("Войдите с помощью биометрии")
            .setNegativeButtonText("Использовать PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info)
    }
}
