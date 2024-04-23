object BotSettings {
    private val VISION_MODEL_IDS = listOf(
        "gpt-4-turbo",
        "gpt-4-turbo-2024-04-09",
        "gpt-4-vision-preview",
        "gpt-4-1106-vision-preview"
    )

    val telegramToken = EnvReader.getEnv("TELEGRAM_BOT_TOKEN")
    val telegramBotUsername = EnvReader.getEnv("TELEGRAM_BOT_USERNAME")
    val telegramBotAdminId = EnvReader.getEnv("TELEGRAM_BOT_ADMIN_ID").toLong()
    val sqlitePath = EnvReader.getEnv("SQLITE_PATH")
    val openAIKey = EnvReader.getEnv("OPENAI_KEY")
    val openAIModel = EnvReader.getEnv("OPENAI_MODEL", "gpt-4-turbo")
    val isVisionModelUsed = VISION_MODEL_IDS.contains(openAIModel)
}