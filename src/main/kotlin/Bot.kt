import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
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
        if (botSettings.isVisionModelUsed) {
            if (update.hasText().not() && update.hasPhotos().not()) {
                sendMessage(update.chatId, "Only text and photos are supported.")
                return
            }
        } else {
            if (update.hasText().not()) {
                sendMessage(update.chatId, "Only text is supported.")
                return
            }
        }
        when (update.toCommandType()) {
            CommandType.START -> handleStartCommand(update)
            CommandType.ADD_USER -> handleAddUserCommand(update)
            CommandType.UNKNOWN -> sendMessage(update.chatId, "Can't parse command")
            CommandType.CANCEL -> handleCancelCommand(update)
            CommandType.IMAGE -> handleImageCommand(update)
            null -> handleGPTResponse(update)
        }
    }

    private fun handleStartCommand(update: Update) {
        val messageText = "Your telegram User ID is: ${update.userId}. Send it to bot administrator."
        sendMessage(update.chatId, messageText)
    }

    private fun handleAddUserCommand(update: Update) {
        val isSentByAdmin = update.userId == botSettings.telegramBotAdminId
        if (isSentByAdmin.not()) {
            sendMessage(update.chatId, "Access is denied")
            return
        }

        runCatching {
            update.message.text.split(" ")[1].toLong()
        }.onSuccess { userId ->
            transaction {
                User.getOrCreate(userId)
                sendMessage(update.chatId, "User added.")
            }
        }.onFailure { sendMessage(update.chatId, "Can't parse user ID.") }
    }

    private fun handleCancelCommand(update: Update) {
        Gpt.clearUserContext(update.userId)
        sendMessage(update.chatId, "Context cleared.")
    }

    private fun handleGPTResponse(update: Update) {
        val userId = update.userId
        if (isUserHasAccess(userId).not()) {
            sendAccessDeniedMessage(update.chatId)
            return
        }

        val chatId = update.chatId
        wrapWithWaiting(
            waitingBody = { sendAction(chatId, "typing") },
            messageBody = {
                val photos = update.message.photo
                if (photos.isNullOrEmpty().not()) {
                    val photo = photos.last()
                    val photoFileId = photo.fileId
                    val photoUrl = execute(GetFile(photoFileId)).getFileUrl(botSettings.telegramToken)
                    Gpt.getResponseForImage(userId, photoUrl, update.message.caption)
                } else Gpt.getResponseForText(userId, update.message.text)
            }
        ).onSuccess {
            sendMessage(chatId, it, ParseMode.MARKDOWN)
        }.onFailure {
            logger.error(it) {}
            sendMessage(chatId, "An error has occurred. Please try calling the /cancel command.")
        }
    }

    private fun handleImageCommand(update: Update) {
        if (isUserHasAccess(update.userId).not()) {
            sendAccessDeniedMessage(update.chatId)
            return
        }

        val prompt = update.message.text.substringAfter("/${CommandType.IMAGE.name.lowercase()} ")
        val chatId = update.chatId
        wrapWithWaiting(
            waitingBody = { sendAction(chatId, "upload_photo") },
            messageBody = { Gpt.generateImage(prompt) }
        ).onSuccess {
            sendPhotoMessage(chatId, it)
        }.onFailure {
            logger.error(it) {}
            sendMessage(chatId, "An image generation error has occurred.")
        }
    }

    private fun wrapWithWaiting(
        waitingBody: () -> Unit,
        messageBody: suspend () -> Result<String>
    ): Result<String> {
        return runBlocking(Dispatchers.IO) {
            val actionJob = launch {
                while (true) {
                    waitingBody.invoke()
                    delay(5.seconds)
                }
            }
            val messageJob = async { messageBody.invoke() }
            messageJob.invokeOnCompletion { actionJob.cancel() }
            messageJob.await()
        }
    }

    private fun isUserHasAccess(userId: Long): Boolean {
        return transaction {
            userId == botSettings.telegramBotAdminId || User.findById(userId) != null
        }
    }

    private fun sendAction(chatId: Long, action: String) {
        val sendChatAction = SendChatAction.builder()
            .chatId(chatId)
            .action(action)
            .build()
        execute(sendChatAction)
    }

    private fun sendMessage(chatId: Long, messageText: String, parseMode: String? = null) {
        runCatching {
            val sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(messageText)
                .parseMode(parseMode)
                .build()
            execute(sendMessage)
        }.onFailure {
            logger.error(it) {}
            // if we tried to send message with markdown, and it failed, try to send it without markdown
            if (parseMode == ParseMode.MARKDOWN) {
                val sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(messageText)
                    .build()
                execute(sendMessage)
            }
        }
    }

    private fun sendPhotoMessage(chatId: Long, url: String) {
        val inputFile = InputFile(url)
        val sendPhoto = SendPhoto.builder()
            .chatId(chatId)
            .photo(inputFile)
            .build()
        execute(sendPhoto)
    }

    private fun sendAccessDeniedMessage(chatId: Long) {
        sendMessage(chatId, "You don't have access.")
    }

    private fun setCommands() {
        val resetCommand = SetMyCommands.builder()
            .command(BotCommand(CommandType.CANCEL.toString().lowercase(), "Clear current conversation state"))
            .command(BotCommand(CommandType.IMAGE.toString().lowercase(), "Generate image"))
            .build()
        execute(resetCommand)
    }

    private fun logSettings() {
        listOf(
            "Bot username: ${botSettings.telegramBotUsername}",
            "OpenAI model: ${botSettings.openAIModel}",
            "Is vision supported: ${botSettings.isVisionModelUsed}"
        ).forEach { message -> logger.info(message) }
    }

    private val Update.chatId get() = message.chatId
    private val Update.userId get() = message.from.id
    private fun Update.hasText() = hasMessage() && message.hasText()
    private fun Update.hasPhotos() = hasMessage() && message.photo.isNullOrEmpty().not()
}