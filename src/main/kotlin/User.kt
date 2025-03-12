import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object UserTable : LongIdTable("user") {
    val model = text("model").nullable()
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(UserTable) {
        fun getOrCreate(telegramUserId: Long): User {
            return findById(telegramUserId) ?: User.new(telegramUserId) {
                model = BotSettings.openAIModel
            }
        }
    }

    var model by UserTable.model
}