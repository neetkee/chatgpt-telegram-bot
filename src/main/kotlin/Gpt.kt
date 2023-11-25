import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlin.time.Duration.Companion.minutes

object Gpt {
    private val openAIConfig = OpenAIConfig(
        token = BotSettings.openAIKey,
        timeout = Timeout(request = 2.minutes),
        logging = LoggingConfig(
            logLevel = LogLevel.None,
            logger = Logger.Default
        )
    )
    private val openAI = OpenAI(openAIConfig)
    private val userContexts = mutableMapOf<Long, MutableList<ChatMessage>>()

    fun clearUserContext(userId: Long) {
        if (userContexts[userId] != null) {
            userContexts[userId] = mutableListOf()
        }
    }

    suspend fun getResponseForText(userId: Long, message: String): Result<String> = runCatching {
        val chatMessage = ChatMessage(
            role = ChatRole.User,
            content = message
        )
        return getResponse(userId, chatMessage)
    }

    suspend fun getResponseForImage(userId: Long, imageUrl: String, imageCaption: String?): Result<String> = runCatching {
        val chatMessage = ChatMessage(
            role = ChatRole.User,
            content = listOfNotNull(
                imageCaption?.let { TextPart(it) },
                ImagePart(imageUrl)
            )
        )
        return getResponse(userId, chatMessage)
    }

    private suspend fun getResponse(userId: Long, chatMessage: ChatMessage): Result<String> {
        userContexts.getOrPut(userId) { mutableListOf() }.add(chatMessage)

        val maxTokens = if (BotSettings.visionModelUsed()) {
            4096
        } else null
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(BotSettings.openAIModel),
            messages = userContexts.getValue(userId),
            maxTokens = maxTokens
        )
        val responseContent = openAI.chatCompletion(chatCompletionRequest).choices
            .firstOrNull()?.message?.content ?: "Answer is empty"
        userContexts.getValue(userId).add(
            ChatMessage(
                role = ChatRole.Assistant,
                content = responseContent
            )
        )
        return Result.success(responseContent)
    }
}