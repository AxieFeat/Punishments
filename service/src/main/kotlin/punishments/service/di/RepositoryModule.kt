package punishments.service.di

import org.koin.dsl.module
import punishments.service.persistence.repository.PunishmentRepository
import punishments.service.persistence.repository.impl.ExposedPunishmentRepository

val repositoryModule = module {
    single<PunishmentRepository> { ExposedPunishmentRepository(get()) }
}
