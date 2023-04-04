package vn.patrick.plugins

import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

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
        var questionList: List<QuestionResult>? = null
        var currentId = 0
        webSocket("/room") {
            val thisConnection = Connection(this)
            connections += thisConnection
            try {
                send("You are connected! There are ${connections.count()} users here.")
                for (frame in incoming) {
                    frame as? Frame.Text ?: continue
                    val receivedText = frame.readText()
                    if (receivedText.contains("start")) {
                        questionList = questionService.getQuestionByDate()
                        connections.forEach {
                            it.session.send("Welcome Admin")
                        }
                    }
                    if (receivedText.contains("answer:")) {
                        if (questionList == null) {
                            thisConnection.session.send("Trận chiến chưa bắt đầu")
                        } else {
                            val answer = receivedText.substringAfter("answer:")
                            val result = questionList!![currentId].answer.single { it.label == answer }
                            if (result.isAnswer!!) {
                                thisConnection.session.send("Bạn trả lời đúng")
                            } else {
                                thisConnection.session.send("Bạn trả lời sai")
                            }
                        }
                    }
                    if (receivedText.contains("next")) {
                        if (questionList == null) {
                            connections.forEach {
                                it.session.send("Please start before return question")
                            }
                        } else if (currentId >= questionList!!.size) {
                            currentId = 0
                            connections.forEach {
                                val question = questionList!![currentId++].copy()
                                question.answer.map { answer ->
                                    answer.isAnswer = null
                                }
                                it.session.sendSerialized(
                                    Data(question)
                                )
                            }
                        } else {
                            connections.forEach {
                                val question = questionList!![currentId++].copy()
                                question.answer.map { answer ->
                                    answer.isAnswer = null
                                }
                                it.session.sendSerialized(
                                    Data(question)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                println(e.localizedMessage)
            } finally {
                println("Removing $thisConnection!")
                connections -= thisConnection
                if (connections.size == 0) {
                    questionList = null
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