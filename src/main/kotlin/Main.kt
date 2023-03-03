import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    Database.connect("jdbc:sqlite:${BotSettings.sqlitePath}", driver = "org.sqlite.JDBC")
    transaction { SchemaUtils.create(UserTable) }
    TelegramBotsApi(DefaultBotSession::class.java).run {
        val bot = Bot(BotSettings)
        registerBot(bot)
    }
}