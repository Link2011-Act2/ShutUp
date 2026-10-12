package jp.linkserver.shutter

import android.content.Context
import kotlinx.coroutines.TimeoutCancellationException

internal enum class AudioPolicyFailure {
    NOT_RUNNING, NOT_AUTHORIZED, SERVICE_START_FAILED, SERVICE_START_TIMEOUT,
    SERVICE_DISCONNECTED, READ_FAILED, QUERY_TIMEOUT
}

internal class AudioPolicyException(val failure: AudioPolicyFailure, cause: Throwable? = null) :
    IllegalStateException(failure.name, cause)

/** Keep technical causes in logcat; show an actionable, localized explanation in the UI. */
internal fun Context.audioPolicyErrorMessage(error: Throwable): String = getString(when {
    error is ShutterVerificationException -> R.string.shutter_change_unverified
    error is TimeoutCancellationException -> R.string.audio_service_start_timeout
    error is AudioPolicyException -> when (error.failure) {
        AudioPolicyFailure.NOT_RUNNING -> R.string.home_start_shizuku_hint
        AudioPolicyFailure.NOT_AUTHORIZED -> R.string.home_authorize_hint
        AudioPolicyFailure.SERVICE_START_FAILED -> R.string.audio_service_start_failed
        AudioPolicyFailure.SERVICE_START_TIMEOUT -> R.string.audio_service_start_timeout
        AudioPolicyFailure.SERVICE_DISCONNECTED -> R.string.audio_service_disconnected
        AudioPolicyFailure.READ_FAILED -> R.string.audio_status_read_failed
        AudioPolicyFailure.QUERY_TIMEOUT -> R.string.audio_status_check_timeout
    }
    else -> R.string.audio_status_read_failed
})
