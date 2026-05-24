package punishments.common

import kotlin.test.Test
import kotlin.test.assertTrue
import punishments.common.config.PunishmentCatalogLoader

class PunishmentCatalogLoaderTest {

    @Test
    fun `loads default catalog from resources`() {
        val catalog = PunishmentCatalogLoader.load()
        assertTrue(catalog.reasons.isNotEmpty(), "Expected default reasons to be present")
        assertTrue(catalog.capabilities.isNotEmpty(), "Expected default capabilities to be present")
    }
}

