package punishments.service.domain.validation

import punishments.common.error.InvalidScopeException
import punishments.common.error.ReasonNotFoundException
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection
import punishments.common.util.ValidationUtils

class InvalidPunishmentRequestException(message: String) :
    IllegalArgumentException(message)

class PunishmentValidator(private val catalog: PunishmentCatalog) {

    fun normalizeTargets(selection: TargetSelection): List<PunishmentTarget> {
        val targets = invalidRequestAsDomainException {
            ValidationUtils.normalizeTargets(selection.targets)
        }
        if (targets.isEmpty()) {
            throw InvalidPunishmentRequestException("At least one resolved target is required")
        }
        return targets
    }

    fun normalizeReasonId(reasonId: String?): String? {
        val normalized = invalidRequestAsDomainException {
            ValidationUtils.normalizeReasonId(reasonId)
        }
        if (normalized != null && catalog.reasonById(normalized) == null) {
            throw ReasonNotFoundException(normalized)
        }
        return normalized
    }

    fun validateActor(actor: PunishmentActor) {
        normalizeActor(actor)
    }

    fun normalizeActor(actor: PunishmentActor): PunishmentActor {
        return invalidRequestAsDomainException {
            actor.copy(name = ValidationUtils.normalizeActorName(actor.name))
        }
    }

    fun validateDuration(durationSeconds: Long?) {
        if (!ValidationUtils.isValidDurationSeconds(durationSeconds)) {
            throw InvalidPunishmentRequestException("Duration must be positive")
        }
    }

    fun effectiveScope(
        requestedScope: PunishmentScope,
        type: PunishmentType,
        reasonId: String?
    ): PunishmentScope {
        if (type == PunishmentType.KICK) {
            return PunishmentScope()
        }

        val keys = when {
            requestedScope.restrictionKeys.isNotEmpty() -> requestedScope.restrictionKeys
            reasonId != null -> catalog.reasonById(reasonId)
                ?.recommendedScopeKeys
                ?.takeIf(Set<String>::isNotEmpty)
                ?: defaultScopeKeys(type)
            else -> defaultScopeKeys(type)
        }

        val normalizedKeys = invalidRequestAsDomainException {
            ValidationUtils.normalizeRestrictionKeys(keys)
        }
        normalizedKeys.forEach { key -> validateScopeKey(key, type) }
        return PunishmentScope(normalizedKeys)
    }

    private fun defaultScopeKeys(type: PunishmentType): Set<String> {
        return catalog.capabilities
            .filter { capability -> capability.appliesTo.isEmpty() || type in capability.appliesTo }
            .map { capability -> capability.key }
            .toSet()
    }

    private fun validateScopeKey(key: String, type: PunishmentType) {
        val capability = catalog.capabilities.firstOrNull { capability -> capability.key == key }
            ?: throw InvalidScopeException(key)
        if (capability.appliesTo.isNotEmpty() && type !in capability.appliesTo) {
            throw InvalidScopeException(key)
        }
    }

    private inline fun <T> invalidRequestAsDomainException(block: () -> T): T {
        return try {
            block()
        } catch (e: IllegalArgumentException) {
            throw InvalidPunishmentRequestException(e.message ?: "Invalid request")
        }
    }
}
