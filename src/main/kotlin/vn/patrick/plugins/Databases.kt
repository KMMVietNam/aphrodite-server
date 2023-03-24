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

fun Application.configureDatabases() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val driverClassName = environment.config.property("storage.driverClassName").getString()
    val jdbcURL = environment.config.property("storage.jdbcURL").getString()
    val database = Database.connect(
            url = jdbcURL,
            driver = driverClassName
    )

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
                    .withExpiresAt(Date(System.currentTimeMillis() + 60000))
                    .sign(Algorithm.HMAC256(secret))
                call.respond(hashMapOf("token" to token))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Email or password is not correct")
            }
        }

        post("/question") {
            val question = call.receive<Question>()
            try {
                questionService.createQuestion(question)
                call.respond(HttpStatusCode.Created, "Created Question")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }

        post("/answer") {
            val answer = call.receive<Answer>()
            try {
                questionService.createAnswer(answer)
                call.respond(HttpStatusCode.Created, "Created Answer")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
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

/*// Read user
        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = userService.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
        // Update user
        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<User>()
            userService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }
        // Delete user
        delete("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }*/
