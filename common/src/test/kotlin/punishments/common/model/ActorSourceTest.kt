package punishments.common.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class ActorSourceTest {

    @Test
    fun `deserialized actor source compares symmetrically with built in source`() {
        val encoded = Json.encodeToString(ActorSource.CONSOLE)
        val decoded = Json.decodeFromString<ActorSource>(encoded)

        assertEquals(ActorSource.CONSOLE, decoded)
        assertEquals(decoded, ActorSource.CONSOLE)
    }
}
