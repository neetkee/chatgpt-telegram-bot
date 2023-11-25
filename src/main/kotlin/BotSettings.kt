object BotSettings {
    private const val VISION_MODEL_ID = "gpt-4-vision-preview"

    val telegramToken = EnvReader.getEnv("TELEGRAM_BOT_TOKEN")
    val telegramBotUsername = EnvReader.getEnv("TELEGRAM_BOT_USERNAME")
    val telegramBotAdminId = EnvReader.getEnv("TELEGRAM_BOT_ADMIN_ID").toLong()
    val sqlitePath = EnvReader.getEnv("SQLITE_PATH")
    val openAIKey = EnvReader.getEnv("OPENAI_KEY")
    val openAIModel = EnvReader.getEnv("OPENAI_MODEL", VISION_MODEL_ID)
    fun visionModelUsed() = openAIModel == VISION_MODEL_ID
}