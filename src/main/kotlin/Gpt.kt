import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

@OptIn(BetaOpenAI::class)
object Gpt {
    private val openAI = OpenAI(BotSettings.openAIKey)
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
            model = ModelId("gpt-3.5-turbo"),
            messages = userContexts.getValue(userId)
        )
        val responseContent = openAI.chatCompletion(chatCompletionRequest).choices.first().message!!.content
        userContexts.getValue(userId).add(
            ChatMessage(
                role = ChatRole.Assistant,
                content = responseContent
            )
        )
        return Result.success(responseContent)
    }
}