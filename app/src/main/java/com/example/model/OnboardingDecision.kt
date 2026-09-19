package com.example.model

/** Pure first-run decision. Persistent values themselves remain in GoalRepository. */
object OnboardingDecision {
    fun shouldShow(onboardingSeen: Boolean, hasSavedGoal: Boolean): Boolean =
        !onboardingSeen && !hasSavedGoal
}
