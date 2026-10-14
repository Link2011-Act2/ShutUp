package jp.linkserver.shutter

import org.junit.Assert.*
import org.junit.Test

class SamsungUserWarningTest {
    private class Store(var consent: SamsungUserConsent = SamsungUserConsent()) : SamsungUserConsentStore {
        var fail = false
        override fun load() = consent
        override fun save(consent: SamsungUserConsent) { check(!fail); this.consent = consent }
    }

    @Test fun detectionUsesTheAppUidAndOnlySamsungNonMainUsersAreAffected() {
        assertEquals(95, AppUserEnvironment.fromAppUid("SaMsUnG", 9_510_263).userId)
        assertTrue(AppUserEnvironment.fromAppUid("SaMsUnG", 9_510_263).needsSamsungUserWarning)
        assertFalse(AppUserEnvironment.fromAppUid("samsung", 10_263).needsSamsungUserWarning)
        assertFalse(AppUserEnvironment.fromAppUid("Google", 9_510_263).needsSamsungUserWarning)
        assertFalse(AppUserEnvironment.fromAppUid("Sony", 110_263).needsSamsungUserWarning)
    }

    @Test fun rememberedConsentSurvivesReloadButDoesNotAuthorizeAutomaticMaintenance() {
        val store = Store()
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 95), store)
        assertTrue(warning.confirmationRequired)
        assertFalse(warning.automaticAllowed)
        warning.confirm(remember = true)
        val reloaded = SamsungUserWarning(AppUserEnvironment("SAMSUNG", 95), store)
        assertTrue(reloaded.confirmed)
        assertFalse(reloaded.confirmationRequired)
        assertFalse(reloaded.automaticAllowed)
        reloaded.requireManualRelease(Any(), null)
        reloaded.enableAutomatic()
        assertTrue(SamsungUserWarning(AppUserEnvironment("samsung", 95), store).automaticAllowed)
    }

    @Test fun uncheckedRememberRequiresTheNextManualConfirmationAndPermitCannotBeReused() {
        val store = Store()
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 95), store)
        val permit = warning.confirm(remember = false)
        val request = Any()
        warning.requireManualRelease(request, permit)
        warning.requireManualRelease(request, permit) // CSC fallback belongs to the same operation.
        assertTrue(warning.confirmationRequired)
        assertThrows(SamsungUserConfirmationRequired::class.java) { warning.requireManualRelease(Any(), permit) }
        assertThrows(SamsungUserConfirmationRequired::class.java) { warning.requireManualRelease(Any(), null) }
        assertTrue(SamsungUserWarning(AppUserEnvironment("samsung", 95), store).confirmationRequired)
        warning.enableAutomatic() // A separate, explicit choice after confirmation.
        assertTrue(warning.automaticAllowed)
        assertTrue(warning.confirmationRequired)
    }

    @Test fun aDifferentEnvironmentCannotInheritConsentOrAnotherWarningsPermit() {
        val store = Store()
        val first = SamsungUserWarning(AppUserEnvironment("samsung", 95), store)
        val permit = first.confirm(remember = true)
        first.enableAutomatic()
        val other = SamsungUserWarning(AppUserEnvironment("samsung", 150), store)
        assertTrue(other.confirmationRequired)
        assertFalse(other.automaticAllowed)
        assertThrows(SamsungUserConfirmationRequired::class.java) { other.requireManualRelease(Any(), permit) }
    }

    @Test fun missingConsentAndFailedPersistenceCannotEnableMaintenanceOrIssueAPermit() {
        val store = Store()
        val warning = SamsungUserWarning(AppUserEnvironment("samsung", 95), store)
        assertThrows(SamsungUserConfirmationRequired::class.java) { warning.enableAutomatic() }
        store.fail = true
        assertThrows(IllegalStateException::class.java) { warning.confirm(remember = true) }
        assertFalse(warning.confirmed)
        assertFalse(warning.automaticAllowed)
        assertTrue(warning.confirmationRequired)
        assertEquals(SamsungUserConsent(), store.consent)
    }

    @Test fun otherManufacturersAndMainUserKeepUnrestrictedBehavior() {
        for (environment in listOf(AppUserEnvironment("samsung", 0), AppUserEnvironment("Google", 95))) {
            val store = Store()
            val warning = SamsungUserWarning(environment, store)
            warning.requireManualRelease(Any(), null)
            warning.enableAutomatic()
            assertFalse(warning.affected)
            assertTrue(warning.automaticAllowed)
            assertFalse(warning.confirmationRequired)
            assertEquals(SamsungUserConsent(), store.consent)
        }
    }
}
