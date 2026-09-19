package com.example

import com.example.model.OnboardingDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDecisionTest {
    @Test fun `clean installation shows onboarding`() {
        assertTrue(OnboardingDecision.shouldShow(onboardingSeen = false, hasSavedGoal = false))
    }

    @Test fun `seen onboarding never returns after an app restart`() {
        assertFalse(OnboardingDecision.shouldShow(onboardingSeen = true, hasSavedGoal = false))
    }

    @Test fun `existing goal suppresses onboarding during upgrade`() {
        assertFalse(OnboardingDecision.shouldShow(onboardingSeen = false, hasSavedGoal = true))
    }
}
