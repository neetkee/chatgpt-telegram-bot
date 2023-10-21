import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import kotlin.time.Duration.Companion.seconds

class Bot(private val botSettings: BotSettings) : TelegramLongPollingBot(botSettings.telegramToken) {
    private val logger = KotlinLogging.logger {}
    override fun getBotUsername(): String = botSettings.telegramBotUsername

    init {
        setCommands()
        logSettings()
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage().not() || update.message.hasText().not()) {
            sendMessage(update.chatId, "Only text is supported.")
            return
        }
        when (update.toCommandType()) {
            CommandType.START -> handleStartCommand(update)
            CommandType.ADD_USER -> handleAddUserCommand(update)
            CommandType.UNKNOWN -> sendMessage(update.chatId, "Can't parse command")
            CommandType.CANCEL -> handleCancelCommand(update)
            null -> handleGPTResponse(update)
        }
    }

    private fun handleStartCommand(update: Update) {
        val messageText = "Your telegram User ID is: ${update.message.from.id}. Send it to bot administrator."
        sendMessage(update.chatId, messageText)
    }

    private fun handleAddUserCommand(update: Update) {
        val isSentByAdmin = update.message.from.id == botSettings.telegramBotAdminId
        if (isSentByAdmin.not()) {
            sendMessage(update.chatId, "Access is denied")
            return
        }

        runCatching {
            update.message.text.split(" ")[1].toLong()
        }.onSuccess { userId ->
            transaction {
                User.saveOrCreate(userId)
                sendMessage(update.chatId, "User added.")
            }
        }.onFailure { sendMessage(update.chatId, "Can't parse user ID.") }
    }

    private fun handleCancelCommand(update: Update) {
        val userId = update.message.from.id
        Gpt.clearUserContext(userId)
        sendMessage(update.chatId, "Context cleared.")
    }

    private fun handleGPTResponse(update: Update) {
        val userId = update.message.from.id
        val userHasAccess = transaction {
            userId == botSettings.telegramBotAdminId || User.findById(userId) != null
        }
        if (userHasAccess.not()) {
            sendMessage(update.chatId, "You don't have access.")
            return
        }

        runBlocking(Dispatchers.IO) {
            val typingJob = launch {
                while (true) {
                    sendTypingAction(update.chatId)
                    delay(5.seconds)
                }
            }
            val getGptResponseJob = async { Gpt.getResponse(userId, update.message.text) }
            getGptResponseJob.invokeOnCompletion { typingJob.cancel() }
            getGptResponseJob.await()
        }
            .onSuccess { sendMessage(update.chatId, it) }
            .onFailure {
                logger.error(it) {}
                sendMessage(update.chatId, "An error has occurred. Please try calling the /cancel command.")
            }
    }

    private fun sendTypingAction(chatId: Long) {
        val sendChatAction = SendChatAction.builder()
            .chatId(chatId)
            .action("typing")
            .build()
        execute(sendChatAction)
    }

    private fun sendMessage(chatId: Long, messageText: String) {
        val sendMessage = SendMessage.builder()
            .chatId(chatId)
            .text(messageText)
            .build()
        execute(sendMessage)
    }

    private fun setCommands() {
        val resetCommand = SetMyCommands.builder()
            .command(BotCommand(CommandType.CANCEL.toString().lowercase(), "Clear current conversation state"))
            .build()
        execute(resetCommand)
    }

    private fun logSettings() {
        val message = """
            Bot username: ${botSettings.telegramBotUsername}.
            OpenAI model: ${botSettings.openAIModel}.
        """.trimIndent()
        logger.info(message)
    }

    private val Update.chatId get() = message.chatId
}