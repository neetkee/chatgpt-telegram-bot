import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.chat.chatMessage
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.image.Quality
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

    suspend fun getResponseForText(userId: Long, model: String, message: String) = runCatching {
        val chatMessage = chatMessage {
            role = ChatRole.User
            content = message
        }
        getResponse(userId, model, chatMessage)
    }

    suspend fun getResponseForImage(userId: Long, model: String, imageUrl: String, imageCaption: String?) = runCatching {
        val chatMessage = chatMessage {
            role = ChatRole.User
            content {
                imageCaption?.let { text(it) }
                image(imageUrl)
            }
        }
        getResponse(userId, model, chatMessage)
    }

    private suspend fun getResponse(userId: Long, model: String, chatMessage: ChatMessage): String {
        userContexts.getOrPut(userId) { mutableListOf() }.add(chatMessage)

        val chatCompletionRequest = chatCompletionRequest {
            this.model = ModelId(model)
            this.messages = userContexts.getValue(userId)
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
        val imageCreation = ImageCreation(
            prompt = prompt,
            n = 1,
            size = ImageSize.is1024x1024,
            user = "url",
            model = ModelId("dall-e-3"),
            quality = Quality.HD
        )
        openAI.imageURL(imageCreation).first().url
    }
}