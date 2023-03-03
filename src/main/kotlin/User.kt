import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

object UserTable : LongIdTable("user")
class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(UserTable) {
        fun saveOrCreate(telegramUserId: Long): User {
            return findById(telegramUserId) ?: User.new(telegramUserId) {}
        }
    }
}