package punishments.common.util

import punishments.common.model.PunishmentTarget
import java.util.Locale

object TargetKeys {

    fun normalized(target: PunishmentTarget): String {
        val targetType = target.targetType.name.uppercase(Locale.ROOT)
        val id = target.id?.toString()?.lowercase(Locale.ROOT)
        if (id != null) {
            return "$targetType:id:$id"
        }

        val name = target.name
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return "$targetType:name:$name"
    }
}
