package dev.androidagent.core

import org.junit.Assert.*
import org.junit.Test

class IntentPolicyTest {

    private val view = "android.intent.action.VIEW"

    @Test fun everyBlockedSchemeIsRefused() {
        for (scheme in IntentPolicy.blockedSchemes) {
            val decision = IntentPolicy.evaluate(view, "$scheme://anything/at/all")
            assertTrue("$scheme should be denied", decision is IntentPolicy.Decision.Deny)
            assertEquals("scheme_blocked", (decision as IntentPolicy.Decision.Deny).reason)
        }
    }

    @Test fun aBlockedSchemeIsStillBlockedInAnyCase() {
        // Scheme comparison is case-insensitive by RFC, so an uppercase
        // spelling must not be a way around the list.
        val decision = IntentPolicy.evaluate(view, "CONTENT://media/external/images/1")
        assertEquals("scheme_blocked", (decision as IntentPolicy.Decision.Deny).reason)
    }

    @Test fun aPrivateAppDeepLinkIsAllowed() {
        // This is the case the layer exists for. A positive scheme allowlist
        // would break it, which is why the rule is structural instead.
        for (uri in listOf("waze://?ll=32.1,34.8&navigate=yes", "spotify:track:xyz", "tg://resolve?domain=x")) {
            val decision = IntentPolicy.evaluate(view, uri)
            assertTrue("$uri should be allowed", decision is IntentPolicy.Decision.Allow)
        }
    }

    @Test fun anActionOutsideTheAllowlistIsRefused() {
        val decision = IntentPolicy.evaluate("android.intent.action.DELETE", "https://example.com")
        assertEquals("action_not_allowed", (decision as IntentPolicy.Decision.Deny).reason)
    }

    @Test fun placingACallDirectlyIsNotAnAvailableAction() {
        // ACTION_CALL dials with no confirmation screen. DIAL reaches the same
        // place and leaves the irreversible press to the user.
        val decision = IntentPolicy.evaluate("android.intent.action.CALL", "tel:+972500000000")
        assertEquals("action_not_allowed", (decision as IntentPolicy.Decision.Deny).reason)
        assertTrue(IntentPolicy.evaluate("android.intent.action.DIAL", "tel:+972500000000")
            is IntentPolicy.Decision.Allow)
    }

    @Test fun aMissingActionDefaultsToView() {
        val decision = IntentPolicy.evaluate(null, "https://example.com")
        assertEquals(view, (decision as IntentPolicy.Decision.Allow).action)
        assertEquals(view, (IntentPolicy.evaluate("  ", "https://example.com")
            as IntentPolicy.Decision.Allow).action)
    }

    @Test fun mainNeedsNoUriAndEverythingElseDoes() {
        assertTrue(IntentPolicy.evaluate("android.intent.action.MAIN", null)
            is IntentPolicy.Decision.Allow)
        assertEquals(
            "uri_required",
            (IntentPolicy.evaluate(view, null) as IntentPolicy.Decision.Deny).reason,
        )
        assertEquals(
            "uri_required",
            (IntentPolicy.evaluate(view, "   ") as IntentPolicy.Decision.Deny).reason,
        )
    }

    @Test fun aUriWithNoSchemeCannotBeResolved() {
        assertEquals(
            "uri_relative",
            (IntentPolicy.evaluate(view, "/settings/wifi") as IntentPolicy.Decision.Deny).reason,
        )
    }

    @Test fun anOverlongUriIsRefusedBeforeItIsParsed() {
        val long = "https://example.com/" + "a".repeat(3_000)
        assertEquals(
            "uri_too_long",
            (IntentPolicy.evaluate(view, long) as IntentPolicy.Decision.Deny).reason,
        )
    }

    @Test fun controlCharactersAndUnparseableTextAreRefused() {
        assertEquals(
            "uri_malformed",
            (IntentPolicy.evaluate(view, "https://example.com/\npath") as IntentPolicy.Decision.Deny).reason,
        )
        assertEquals(
            "uri_malformed",
            (IntentPolicy.evaluate(view, "http://exa mple.com") as IntentPolicy.Decision.Deny).reason,
        )
    }

    @Test fun sendingAMessageAsksFirst() {
        for (uri in listOf("mailto:a@b.com?subject=hi", "sms:+972500000000?body=hi", "smsto:+972500000000")) {
            val decision = IntentPolicy.evaluate(view, uri)
            assertTrue("$uri should need confirmation", decision is IntentPolicy.Decision.NeedsConfirmation)
            assertTrue((decision as IntentPolicy.Decision.NeedsConfirmation).what.isNotBlank())
        }
    }

    @Test fun plainNavigationDoesNotAskFirst() {
        for (uri in listOf("tel:+972500000000", "geo:32.08,34.78?q=cafe", "https://maps.google.com/?q=cafe")) {
            assertTrue("$uri should be allowed", IntentPolicy.evaluate(view, uri) is IntentPolicy.Decision.Allow)
        }
    }

    @Test fun aPrefilledMessageOnAMessagingHostAsksFirst() {
        assertTrue(
            IntentPolicy.evaluate(view, "https://wa.me/972500000000?text=hi")
                is IntentPolicy.Decision.NeedsConfirmation,
        )
        // The same host without a payload only opens a chat, which is navigation.
        assertTrue(
            IntentPolicy.evaluate(view, "https://wa.me/972500000000")
                is IntentPolicy.Decision.Allow,
        )
    }

    @Test fun confirmationDowngradesToAllow() {
        val decision = IntentPolicy.evaluate(view, "mailto:a@b.com?subject=hi", userConfirmed = true)
        assertTrue(decision is IntentPolicy.Decision.Allow)
    }

    @Test fun confirmationCanNeverUnblockADenial() {
        // The whole point of the split: a user saying yes to a message is not
        // a user granting access to content:// or to an arbitrary action.
        assertTrue(
            IntentPolicy.evaluate(view, "content://media/external/images/1", userConfirmed = true)
                is IntentPolicy.Decision.Deny,
        )
        assertTrue(
            IntentPolicy.evaluate("android.intent.action.CALL", "tel:+1", userConfirmed = true)
                is IntentPolicy.Decision.Deny,
        )
        assertTrue(
            IntentPolicy.evaluate(view, "intent://scan/#Intent;scheme=zxing;end", userConfirmed = true)
                is IntentPolicy.Decision.Deny,
        )
    }

    @Test fun sendToAlwaysAsksFirst() {
        val decision = IntentPolicy.evaluate("android.intent.action.SENDTO", "https://example.com/x")
        assertTrue(decision is IntentPolicy.Decision.NeedsConfirmation)
    }
}
