object BotSettings {
    val telegramToken = EnvReader.getEnv("TELEGRAM_BOT_TOKEN")
    val telegramBotUsername = EnvReader.getEnv("TELEGRAM_BOT_USERNAME")
    val telegramBotAdminId = EnvReader.getEnv("TELEGRAM_BOT_ADMIN_ID").toLong()
    val sqlitePath = EnvReader.getEnv("SQLITE_PATH")
    val openAIKey = EnvReader.getEnv("OPENAI_KEY")
    val openAIModel = EnvReader.getEnv("OPENAI_MODEL", "gpt-3.5-turbo")
}