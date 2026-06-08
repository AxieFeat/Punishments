package punishments.service.grpc

import io.grpc.Status
import io.grpc.StatusException
import org.slf4j.LoggerFactory
import punishments.common.error.PunishmentException
import punishments.common.grpc.CreatePunishmentProto
import punishments.common.grpc.CreatePunishmentResultProto
import punishments.common.grpc.GetCatalogProto
import punishments.common.grpc.GetPunishmentDetailsProto
import punishments.common.grpc.GetPunishmentsProto
import punishments.common.grpc.GetTargetPunishmentsProto
import punishments.common.grpc.PaginatedPunishmentsProto
import punishments.common.grpc.PunishmentResponseProto
import punishments.common.grpc.PunishmentServiceGrpcKt
import punishments.common.grpc.ReasonCatalogProto
import punishments.common.grpc.RevokePunishmentProto
import punishments.common.grpc.RevokePunishmentResultProto
import punishments.common.grpc.SearchPunishmentsProto
import punishments.common.protocol.PunishmentAPI
import punishments.service.grpc.mapper.ProtoMapper.toDomain
import punishments.service.grpc.mapper.ProtoMapper.toGrpcStatus
import punishments.service.grpc.mapper.ProtoMapper.toProto

class PunishmentGrpcService(
    private val api: PunishmentAPI
) : PunishmentServiceGrpcKt.PunishmentServiceCoroutineImplBase() {

    private val logger = LoggerFactory.getLogger(PunishmentGrpcService::class.java)

    override suspend fun createPunishment(request: CreatePunishmentProto): CreatePunishmentResultProto {
        return handle("CreatePunishment") {
            api.createPunishment(request.toDomain()).toProto()
        }
    }

    override suspend fun revokePunishment(request: RevokePunishmentProto): RevokePunishmentResultProto {
        return handle("RevokePunishment") {
            api.revokePunishment(request.toDomain()).toProto()
        }
    }

    override suspend fun getPunishmentDetails(request: GetPunishmentDetailsProto): PunishmentResponseProto {
        return handle("GetPunishmentDetails") {
            api.getPunishmentDetails(request.toDomain()).toProto()
        }
    }

    override suspend fun getPunishments(request: GetPunishmentsProto): PaginatedPunishmentsProto {
        return handle("GetPunishments") {
            api.getPunishments(request.toDomain()).toProto()
        }
    }

    override suspend fun getTargetPunishments(request: GetTargetPunishmentsProto): PaginatedPunishmentsProto {
        return handle("GetTargetPunishments") {
            api.getTargetPunishments(request.toDomain()).toProto()
        }
    }

    override suspend fun searchPunishments(request: SearchPunishmentsProto): PaginatedPunishmentsProto {
        return handle("SearchPunishments") {
            api.searchPunishments(request.toDomain()).toProto()
        }
    }

    override suspend fun getCatalog(request: GetCatalogProto): ReasonCatalogProto {
        return handle("GetCatalog") {
            api.getCatalog(request.toDomain()).toProto()
        }
    }

    private inline fun <T> handle(method: String, block: () -> T): T {
        return try {
            block()
        } catch (e: PunishmentException) {
            logger.warn("gRPC {} failed: {} - {}", method, e.errorCode, e.message)
            throw StatusException(e.toGrpcStatus())
        } catch (e: IllegalArgumentException) {
            logger.warn("gRPC {} bad request: {}", method, e.message)
            throw StatusException(Status.INVALID_ARGUMENT.withDescription(e.message))
        } catch (e: Exception) {
            logger.error("gRPC {} internal error", method, e)
            throw StatusException(Status.INTERNAL.withDescription("Internal error"))
        }
    }
}
