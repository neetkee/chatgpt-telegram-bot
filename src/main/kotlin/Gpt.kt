import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
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

    suspend fun getResponse(userId: Long, message: String): Result<String> = runCatching {
        val chatCompletionMessage = ChatMessage(
            role = ChatRole.User,
            content = message
        )
        userContexts.getOrPut(userId) { mutableListOf() }.add(chatCompletionMessage)

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(BotSettings.openAIModel),
            messages = userContexts.getValue(userId)
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