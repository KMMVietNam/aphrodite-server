package vn.patrick.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jetbrains.exposed.sql.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.lang.Exception
import java.util.*

fun Application.configureDatabases(
    database: Database,
    audience: String,
    issuer: String,
    secret: String
) {
    val userService = UserService(database)
    val questionService = QuestionService(database)

    routing {
        // Create user
        post("/users") {
            val user = call.receive<User>()
            val id = userService.create(user)
            call.respond(HttpStatusCode.Created, "Create user success!")
        }

        post("/login") {
            val user = call.receive<User>()
            // Check username and password
            // ...
            val userDB = userService.search(user.name)
            if (userDB != null && userDB.password == user.password) {
                val token = JWT.create()
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .withClaim("username", user.name)
                    .withExpiresAt(Date(System.currentTimeMillis() + 80000000))
                    .sign(Algorithm.HMAC256(secret))
                call.respond(hashMapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Email or password is not correct")
            }
        }

        get("/questions") {
            try {
                val result = questionService.getAll()
                call.respond(HttpStatusCode.OK, result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

    }
}
