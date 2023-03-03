import org.telegram.telegrambots.meta.api.objects.Update

enum class CommandType {
    START, ADD_USER, CANCEL, UNKNOWN
}

fun Update.toCommandType(): CommandType? {
    if (message.isCommand.not()) return null

    return runCatching {
        message.text
            .split(" ")
            .first()
            .drop(1)
            .let { CommandType.valueOf(it.uppercase()) }
    }.getOrElse { CommandType.UNKNOWN }
}