import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

private val logger = KotlinLogging.logger {}

fun main() {
    Flyway
        .configure()
        .dataSource("jdbc:sqlite:${BotSettings.sqlitePath}", "", "")
        .baselineOnMigrate(true)
        .load()
        .migrate()
    Database.connect("jdbc:sqlite:${BotSettings.sqlitePath}", driver = "org.sqlite.JDBC")
    TelegramBotsLongPollingApplication().run {
        registerBot(BotSettings.telegramToken, Bot())
        logger.info { "Bot started." }
    }
}