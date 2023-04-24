package vn.patrick.plugins

import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*

@Serializable
data class User(
    val name: String,
    val password: Int,
    val id: Int? = null,
    val avatar: String? = null
)

@Serializable
data class UserProfile(
    val name: String? = null,
    val avatar: String? = null
)

class UserService(private val database: Database) {
    object Users : Table("user_table") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", length = 50)
        val avatar = varchar("avatar", length = 1000).nullable()
        val password = integer("password")
        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun create(user: User): Int = dbQuery {
        Users.insert {
            it[name] = user.name
            it[password] = user.password
        }[Users.id]
    }

    suspend fun read(id: Int): User? {
        return dbQuery {
            Users.select { Users.id eq id }
                .map { User(it[Users.name], it[Users.password], avatar = it[Users.avatar]) }
                .singleOrNull()
        }
    }

    suspend fun search(name: String): User? {
        return dbQuery {
            Users.select { Users.name eq name }
                .map { User(it[Users.name], it[Users.password], it[Users.id]) }
                .singleOrNull()
        }
    }

    suspend fun updateUserProfile(id: Int, user: UserProfile) {
        dbQuery {
            Users.update({ Users.id eq id }) {userDb ->
                user.name?.let {
                    userDb[name] = it
                }
                user.avatar?.let {
                    userDb[avatar] = it
                }
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.id.eq(id) }
        }
    }
}