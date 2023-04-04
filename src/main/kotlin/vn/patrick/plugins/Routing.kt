package vn.patrick.plugins

import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
import java.lang.Exception

@Serializable
data class QuestionRequest(
    val questionContent: String,
    val questionImage: String,
    val answer: List<Answer>
)

fun Application.configureRouting(
    database: Database,
    audience: String,
    issuer: String,
    secret: String
) {
    val questionService = QuestionService(database)
    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        authenticate("auth-jwt") {
            get("/expiredtime") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
            }
        }

        post("/question") {
            try {
                val data = call.receive<QuestionRequest>()
                val question = Question(
                    data.questionContent,
                    data.questionImage,
                )
                val questionId = questionService.createQuestion(question)
                data.answer.forEach {
                    questionService.createAnswer(it, questionId)
                }
                call.respond(HttpStatusCode.Created, "Created Question")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Error")
            }
        }
    }
}
