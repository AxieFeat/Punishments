package punishments.service.domain.validation

import punishments.common.error.InvalidScopeException
import punishments.common.error.ReasonNotFoundException
import punishments.common.model.PunishmentActor
import punishments.common.model.PunishmentCatalog
import punishments.common.model.PunishmentScope
import punishments.common.model.PunishmentTarget
import punishments.common.model.PunishmentType
import punishments.common.model.TargetSelection

class InvalidPunishmentRequestException(message: String) :
    IllegalArgumentException(message)

class PunishmentValidator(private val catalog: PunishmentCatalog) {

    fun normalizeTargets(selection: TargetSelection): List<PunishmentTarget> {
        val targets = selection.targets.filter { target ->
            target.id != null || !target.name.isNullOrBlank()
        }.distinctBy { target ->
            target.id?.toString() ?: "${target.kind.name}:${target.name.orEmpty().lowercase()}"
        }
        if (targets.isEmpty()) {
            throw InvalidPunishmentRequestException("At least one resolved target is required")
        }
        return targets
    }

    fun normalizeReasonId(reasonId: String?): String? {
        val normalized = reasonId?.trim()?.takeIf(String::isNotBlank)
        if (normalized != null && normalized.length > REASON_ID_MAX_LENGTH) {
            throw InvalidPunishmentRequestException("Reason id is too long")
        }
        if (normalized != null && catalog.reasonById(normalized) == null) {
            throw ReasonNotFoundException(normalized)
        }
        return normalized
    }

    fun validateActor(actor: PunishmentActor) {
        if (actor.name.isBlank()) {
            throw InvalidPunishmentRequestException("Actor name is required")
        }
    }

    fun validateDuration(durationSeconds: Long?) {
        if (durationSeconds != null && durationSeconds <= 0) {
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

        keys.forEach { key -> validateScopeKey(key, type) }
        return PunishmentScope(keys)
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

    private companion object {
        const val REASON_ID_MAX_LENGTH = 64
    }
}
