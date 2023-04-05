package vn.patrick.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

fun Application.configureWebSocket(database: Database) {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
        val questionService = QuestionService(database)
        val userService = UserService(database)
        var questionList: List<QuestionResult>? = null
        var answerList: List<AnswerSocket?>? = null
        var currentId = -1
        authenticate("auth-jwt") {
            webSocket("/room") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val user = userService.search(username)
                if (user != null) {
                    val thisConnection = Connection(this)
                    connections += thisConnection
                    try {
                        send("Welcome $username to room! There are ${connections.count()} users here.")
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val receivedText = frame.readText()
                            when {
                                receivedText.contains("start") -> {
                                    questionList = questionService.getQuestionByDate()
                                    answerList = List(questionList!!.size) { AnswerSocket(answer = arrayListOf()) }
                                    connections.forEach {
                                        it.session.send("Welcome Admin")
                                    }
                                }
                                receivedText.contains("next") -> {
                                    if (questionList == null) {
                                        connections.forEach {
                                            it.session.send("Please start before return question")
                                        }
                                    } else if (currentId >= questionList!!.size - 1) {
                                        currentId = -1
                                        val question = questionList!![++currentId].copy()
                                        connections.forEach {
                                            it.session.sendSerialized(
                                                Data(question)
                                            )
                                        }
                                    } else {
                                        val question = questionList!![++currentId].copy()
                                        connections.forEach {
                                            it.session.sendSerialized(
                                                Data(question)
                                            )
                                        }
                                    }
                                }
                                receivedText.contains("currentid") -> {
                                    thisConnection.session.send(currentId.toString())
                                }
                                receivedText.contains("listlength") -> {
                                    thisConnection.session.send(questionList!!.size.toString())
                                }
                                receivedText.contains("answer:") -> {
                                    if (questionList == null) {
                                        thisConnection.session.send("Trận chiến chưa bắt đầu")
                                    } else {
                                        val answer = receivedText.substringAfter("answer:")
                                        val result = questionList!![currentId].answer.single { it.label == answer }
                                        var isTrue = false
                                        if (result.isAnswer!!) {
                                            isTrue = true
                                            thisConnection.session.send("Bạn trả lời đúng")
                                        } else {
                                            isTrue = false
                                            thisConnection.session.send("Bạn trả lời sai")
                                        }
                                        answerList!![currentId]!!.answer.add(UserAndAnswer(username, isTrue))
                                    }
                                }
                                receivedText.contains("showresult") -> {
                                    thisConnection.session.sendSerialized(answerList!![currentId])
                                }
                            }
                        }
                    } catch (e: Exception) {
                        this@configureWebSocket.log.error("Socket Error:" + e.printStackTrace())
                    } finally {
                        println("Removing $thisConnection!")
                        connections -= thisConnection
                        if (connections.size == 0) {
                            currentId = -1
                            questionList = null
                        }
                    }
                } else {
                    send("Sai token rồi ông ơi, định hack à?")
                }
            }
        }

    }
}

class Connection(val session: DefaultWebSocketServerSession) {
    companion object {
        val lastId = AtomicInteger(0)
    }

    val name = "user${lastId.getAndIncrement()}"
}

@Serializable
data class AnswerSocket(
    val answer: ArrayList<UserAndAnswer>
)

@Serializable
data class UserAndAnswer(
    val userName: String,
    val answer: Boolean,
)