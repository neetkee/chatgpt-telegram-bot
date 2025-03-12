import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import org.jetbrains.exposed.sql.transactions.transaction
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.ParseMode
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class Bot : LongPollingUpdateConsumer {
    private val logger = KotlinLogging.logger {}
    private val telegramClient = OkHttpTelegramClient(BotSettings.telegramToken)

    private val updatesScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatProcessors = ConcurrentHashMap<Long, Channel<Update>>()

    init {
        setCommands()
        addAdminUser()
    }

    override fun consume(updates: List<Update>) {
        updates.forEach { update ->
            val channel = getOrCreateChatChannel(update.chatId)
            updatesScope.launch {
                channel.send(update)
            }
        }
    }

    private fun getOrCreateChatChannel(chatId: Long): Channel<Update> {
        return chatProcessors.computeIfAbsent(chatId) { _ ->
            val channel = Channel<Update>(Channel.UNLIMITED)
            updatesScope.launch {
                for (updateToProcess in channel) {
                    runCatching { consume(updateToProcess) }
                        .onFailure { logger.error(it) { "Error processing update for chat $chatId" } }
                }
            }
            channel
        }
    }

    private fun consume(update: Update) {
        if (update.hasText().not() && update.hasPhotos().not()) {
            sendMessage(update.chatId, "Unsupported message type.")
            return
        }
        when (update.toCommandType()) {
            CommandType.START -> handleStartCommand(update)
            CommandType.ADD_USER -> handleAddUserCommand(update)
            CommandType.CANCEL -> handleCancelCommand(update)
            CommandType.IMAGE -> handleImageCommand(update)
            CommandType.SET_MODEL -> handleSetModelCommand(update)
            CommandType.UNKNOWN -> sendMessage(update.chatId, "Can't parse command")
            null -> handleGPTResponse(update)
        }
    }

    private fun handleStartCommand(update: Update) {
        val messageText = "Your telegram User ID is: ${update.userId}. Send it to bot administrator."
        sendMessage(update.chatId, messageText)
    }

    private fun handleAddUserCommand(update: Update) {
        val isSentByAdmin = update.userId == BotSettings.telegramBotAdminId
        if (isSentByAdmin.not()) {
            sendMessage(update.chatId, "Access is denied")
            return
        }

        runCatching { parseCommandArgument(update).toLong() }
            .onSuccess { userId ->
                transaction {
                    User.getOrCreate(userId)
                    sendMessage(update.chatId, "User added.")
                }
            }
            .onFailure { sendMessage(update.chatId, "Can't parse user ID.") }
    }

    private fun handleSetModelCommand(update: Update) {
        transaction {
            val user = User.findById(update.userId)
            if (user == null) {
                sendAccessDeniedMessage(update.chatId)
                return@transaction
            }

            runCatching { parseCommandArgument(update) }
                .onSuccess { model ->
                    user.model = model
                    sendMessage(update.chatId, "Model set.")
                }
                .onFailure { sendMessage(update.chatId, "Can't parse model ID.") }
        }
    }

    private fun handleCancelCommand(update: Update) {
        Gpt.clearUserContext(update.userId)
        sendMessage(update.chatId, "Context cleared.")
    }

    private fun handleGPTResponse(update: Update) {
        val userId = update.userId
        val user = transaction { User.findById(userId) }
        if (user == null) {
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
                    val photoUrl = telegramClient.execute(GetFile(photoFileId)).getFileUrl(BotSettings.telegramToken)
                    Gpt.getResponseForImage(userId, getModel(user), photoUrl, update.message.caption)
                } else Gpt.getResponseForText(userId, getModel(user), update.message.text)
            }
        ).onSuccess {
            sendMessage(chatId, it, ParseMode.MARKDOWN)
        }.onFailure {
            logger.error(it) {}
            sendMessage(chatId, it.message ?: "Unknown error.")
        }
    }

    private fun handleImageCommand(update: Update) {
        val user = transaction { User.findById(update.userId) }
        if (user == null) {
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

    private fun sendAction(chatId: Long, action: String) {
        val sendChatAction = SendChatAction.builder()
            .chatId(chatId)
            .action(action)
            .build()
        telegramClient.execute(sendChatAction)
    }

    private fun sendMessage(chatId: Long, messageText: String, parseMode: String? = null) {
        runCatching {
            val sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(messageText)
                .parseMode(parseMode)
                .build()
            telegramClient.execute(sendMessage)
        }.onFailure {
            logger.error(it) {}
            // if we tried to send message with markdown, and it failed, try to send it without markdown
            if (parseMode == ParseMode.MARKDOWN) {
                val sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(messageText)
                    .build()
                telegramClient.execute(sendMessage)
            }
        }
    }

    private fun sendPhotoMessage(chatId: Long, url: String) {
        val inputFile = InputFile(url)
        val sendPhoto = SendPhoto.builder()
            .chatId(chatId)
            .photo(inputFile)
            .build()
        telegramClient.execute(sendPhoto)
    }

    private fun sendAccessDeniedMessage(chatId: Long) {
        sendMessage(chatId, "You don't have access.")
    }

    private fun setCommands() {
        val resetCommand = SetMyCommands.builder()
            .command(BotCommand(CommandType.CANCEL.toString().lowercase(), "Clear current conversation state"))
            .command(BotCommand(CommandType.IMAGE.toString().lowercase(), "Generate image"))
            .command(BotCommand(CommandType.SET_MODEL.toString().lowercase(), "Set model"))
            .build()
        telegramClient.execute(resetCommand)
    }

    private fun addAdminUser() {
        transaction {
            User.getOrCreate(BotSettings.telegramBotAdminId)
        }
    }

    private fun getModel(user: User): String {
        return user.model ?: BotSettings.openAIModel
    }

    private fun parseCommandArgument(update: Update): String {
        return update.message.text.split(" ")[1]
    }

    private val Update.chatId get() = message.chatId
    private val Update.userId get() = message.from.id
    private fun Update.hasText() = hasMessage() && message.hasText()
    private fun Update.hasPhotos() = hasMessage() && message.photo.isNullOrEmpty().not()
}