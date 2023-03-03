import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI

@OptIn(BetaOpenAI::class)
object Gpt {
    private val openAI = OpenAI(EnvReader.getEnv("OPENAI_KEY"))
    private val userContexts = mutableMapOf<Long, MutableList<ChatMessage>>()

    fun clearUserContext(userId: Long) {
        if (userContexts[userId] != null) {
            userContexts[userId] = mutableListOf()
        }
    }

    suspend fun getResponse(userId: Long, message: String): String {
        val chatCompletionMessage = ChatMessage(
            role = ChatRole("user"),
            content = message
        )
        userContexts.getOrPut(userId) { mutableListOf() }.add(chatCompletionMessage)

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo-0301"),
            messages = userContexts.getValue(userId)
        )
        val responseContent = openAI.chatCompletion(chatCompletionRequest).choices.first().message!!.content
        userContexts.getValue(userId).add(
            ChatMessage(
                role = ChatRole("assistant"),
                content = responseContent
            )
        )
        return responseContent
    }
}