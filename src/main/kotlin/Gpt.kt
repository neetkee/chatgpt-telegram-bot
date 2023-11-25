import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.chat.chatMessage
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.image.imageCreation
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlin.time.Duration.Companion.minutes

@OptIn(BetaOpenAI::class)
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

    suspend fun getResponseForText(userId: Long, message: String) = runCatching {
        val chatMessage = chatMessage {
            role = ChatRole.User
            content = message
        }
        getResponse(userId, chatMessage)
    }

    suspend fun getResponseForImage(userId: Long, imageUrl: String, imageCaption: String?) = runCatching {
        val chatMessage = chatMessage {
            role = ChatRole.User
            content {
                imageCaption?.let { text(it) }
                image(imageUrl)
            }
        }
        getResponse(userId, chatMessage)
    }

    private suspend fun getResponse(userId: Long, chatMessage: ChatMessage): String {
        userContexts.getOrPut(userId) { mutableListOf() }.add(chatMessage)

        val chatCompletionRequest = chatCompletionRequest {
            this.model = ModelId(BotSettings.openAIModel)
            this.messages = userContexts.getValue(userId)
            this.maxTokens = if (BotSettings.visionModelUsed()) {
                4096
            } else null
        }
        val responseContent = openAI.chatCompletion(chatCompletionRequest).choices
            .firstOrNull()?.message?.content ?: "Answer is empty"
        userContexts.getValue(userId).add(
            ChatMessage(
                role = ChatRole.Assistant,
                content = responseContent
            )
        )
        return responseContent
    }

    suspend fun generateImage(prompt: String) = runCatching {
        val imageCreation = imageCreation {
            this.prompt = prompt
            this.n = 1
            this.model = ModelId("dall-e-3")
            this.user = "url"
            this.size = ImageSize.is1024x1024
        }
        openAI.imageURL(imageCreation).first().url
    }
}