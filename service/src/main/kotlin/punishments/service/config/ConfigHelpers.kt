package punishments.service.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory

internal val applicationConfig: Config by lazy { ConfigFactory.load() }

internal fun Config.string(path: String, default: String): String {
    return if (hasPath(path)) getString(path) else default
}

internal fun Config.int(path: String, default: Int): Int {
    return if (hasPath(path)) getInt(path) else default
}

internal fun Config.stringList(path: String, default: List<String>): List<String> {
    return try {
        if (hasPath(path)) getStringList(path) else default
    } catch (_: ConfigException.WrongType) {
        listOf(getString(path))
    }
}
