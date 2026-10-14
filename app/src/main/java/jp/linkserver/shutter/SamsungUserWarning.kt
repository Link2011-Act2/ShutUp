package jp.linkserver.shutter

internal data class SamsungUserConsent(
    val environment: String? = null,
    val skipWarning: Boolean = false,
    val automaticAllowed: Boolean = false
)

internal interface SamsungUserConsentStore {
    fun load(): SamsungUserConsent
    fun save(consent: SamsungUserConsent)
}

internal class SamsungUserConfirmationRequired : IllegalStateException("Samsung non-main user confirmation is required")

internal interface ReleaseAuthorization {
    val confirmationRequired: Boolean get() = false
    val automaticAllowed: Boolean get() = true
    fun requireManualRelease(request: Any, permit: ReleasePermit?) {}
    fun enableAutomatic() {}
    fun requireAutomaticRelease() {
        if (!automaticAllowed) throw SamsungUserConfirmationRequired()
    }
}

internal object UnrestrictedReleaseAuthorization : ReleaseAuthorization

/** A confirmation with 'do not show again' off authorizes exactly one manual request. */
internal class ReleasePermit internal constructor(private val owner: SamsungUserWarning) {
    private var claimedBy: Any? = null
    @Synchronized internal fun claim(issuer: SamsungUserWarning, request: Any): Boolean {
        if (owner !== issuer || claimedBy != null && claimedBy !== request) return false
        claimedBy = request
        return true
    }
}

/** Shared by UI, tile, notification, automatic events and Worker through the coordinator. */
internal class SamsungUserWarning(
    val environment: AppUserEnvironment,
    private val store: SamsungUserConsentStore
) : ReleaseAuthorization {
    val affected get() = environment.needsSamsungUserWarning
    private val key = "samsung:${environment.userId}"
    private var consent = store.load()
    @get:Synchronized val confirmed get() = !affected || consent.environment == key
    @get:Synchronized override val confirmationRequired get() = affected && (!confirmed || !consent.skipWarning)
    @get:Synchronized override val automaticAllowed get() = !affected || confirmed && consent.automaticAllowed

    @Synchronized fun confirm(remember: Boolean): ReleasePermit {
        if (affected) {
            val next = SamsungUserConsent(key, remember, confirmed && consent.automaticAllowed)
            store.save(next)
            consent = next
        }
        return ReleasePermit(this)
    }

    @Synchronized override fun requireManualRelease(request: Any, permit: ReleasePermit?) {
        if (confirmationRequired && permit?.claim(this, request) != true) throw SamsungUserConfirmationRequired()
    }

    @Synchronized override fun enableAutomatic() {
        if (!affected) return
        if (!confirmed) throw SamsungUserConfirmationRequired()
        val next = consent.copy(automaticAllowed = true)
        store.save(next)
        consent = next
    }
}
