package com.example.fitapp.feature.profile.domain

import com.example.fitapp.core.network.ApiFailure
import com.example.fitapp.core.util.AppResult
import javax.inject.Inject

/**
 * Asks the server for targets and falls back to the on-device calculator when it cannot be reached,
 * so onboarding never blocks on the network. A server rejection (bad input) is passed through.
 */
class SuggestTargetsUseCase
    @Inject
    constructor(private val repository: ProfileRepository) {
        suspend operator fun invoke(input: TargetSuggestionInput): AppResult<SuggestedTargets> =
            when (val remote = repository.suggestTargets(input)) {
                is AppResult.Success -> remote
                is AppResult.Failure ->
                    when (remote.failure) {
                        is ApiFailure.Invalid -> remote
                        else -> AppResult.Success(LocalTargetCalculator.suggest(input))
                    }
            }
    }

/**
 * Finishes onboarding. Targets are written first and the profile last: the profile becoming
 * complete is what moves the app past onboarding, so it must be the final write.
 */
class CompleteOnboardingUseCase
    @Inject
    constructor(private val repository: ProfileRepository) {
        suspend operator fun invoke(
            profile: UserProfile,
            targets: Targets,
        ): AppResult<Unit> {
            val savedTargets = repository.saveTargets(targets)
            if (savedTargets is AppResult.Failure) return savedTargets
            return repository.saveProfile(profile)
        }
    }
