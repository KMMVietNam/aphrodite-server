package vn.patrick.plugins

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import java.time.Duration
import java.time.LocalDateTime
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
                val userid = principal.payload.getClaim("userid").asInt()
                val user = userService.search(username)
                if (user != null) {
                    val thisConnection = Connection(this)
                    val thisConnection2 = Connection(this)
                    connections += thisConnection
                    try {
                        sendSerialized(DataText(result = "Welcome $username to room! There are ${connections.count()} users here."))
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val receivedText = frame.readText()
                            when {
                                receivedText.contains("start") -> {
                                    questionList = questionService.getQuestionByDate()
                                    answerList = List(questionList!!.size) { AnswerSocket(answer = arrayListOf()) }
                                    connections.forEach {
                                        it.session.sendSerialized(DataText(result = "Welcome Admin"))
                                    }
                                }
                                receivedText.contains("next") -> {
                                    if (questionList == null) {
                                        connections.forEach {
                                            it.session.sendSerialized(DataText(result = "Please start before return question"))
                                        }
                                    } else if (currentId >= questionList!!.size - 1) {
                                        currentId = -1
                                        val question = questionList!![++currentId].copy()
                                        this@configureWebSocket.log.debug("Gửi câu hỏi")
                                        launch {
                                            sendQuestion(
                                                connections,
                                                question,
                                                answerList!!,
                                                currentId
                                            )
                                        }

                                    } else {
                                        val question = questionList!![++currentId].copy()
                                        this@configureWebSocket.log.debug("Gửi câu hỏi")
                                        launch {
                                            sendQuestion(
                                                connections,
                                                question,
                                                answerList!!,
                                                currentId
                                            )
                                        }
                                    }
                                }
                                receivedText.contains("currentid") -> {
                                    thisConnection2.session.sendSerialized(DataText(result = currentId.toString()))
                                }
                                receivedText.contains("listlength") -> {
                                    this@configureWebSocket.log.debug("Độ dài của list:" + questionList!!.size.toString())
                                    thisConnection2.session.sendSerialized(DataText(result = questionList!!.size.toString()))
                                }
                                receivedText.contains("timer") -> {
                                    for(i in 0..25){
                                        connections.forEach {
                                            it.session.sendSerialized(DataText(progress = i.toString()))
                                        }
                                        delay(1000)
                                    }
                                }
                                receivedText.contains("answer:") -> {
                                    val time = LocalDateTime.now().toString()
                                    withContext(Dispatchers.IO) {
                                        if (questionList == null) {
                                            thisConnection.session.sendSerialized(DataText(result ="Trận chiến chưa bắt đầu"))
                                        } else {
                                            val answer = receivedText.substringAfter("answer:")
                                            val result = questionList!![currentId].answer.single { it.label == answer }
                                            var isTrue = false
                                            if (result.isAnswer!!) {
                                                isTrue = true
                                                thisConnection.session.sendSerialized(DataText(result ="Bạn trả lời đúng"))
                                            } else {
                                                isTrue = false
                                                thisConnection.session.sendSerialized(DataText(result ="Bạn trả lời sai"))
                                            }
                                            val userDB = userService.read(userid)
                                            answerList!![currentId]!!.answer.add(UserAndAnswer(
                                                userDB?.name,
                                                userDB?.avatar,
                                                time,
                                                isTrue))
                                        }
                                    }
                                }
                                receivedText.contains("showresult") -> {
                                    thisConnection.session.sendSerialized(sortUserRankPerQuestion(answerList!![currentId]))
                                }
                                receivedText.contains("showtotalresult") -> {
                                    thisConnection.session.sendSerialized(sortUserTotalRankAtCurrentQuestion(answerList))
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

private suspend fun sendQuestion(
    connections: Set<Connection>,
    question: QuestionResult,
    answerList: List<AnswerSocket?>,
    currentId: Int
) {
    connections.forEach {
        it.session.sendSerialized(
            Data(question)
        )
    }
    for (i in 0..30) {
        connections.forEach {
            it.session.sendSerialized(DataText(progress = i.toString()))
        }
        delay(1000)
    }
    connections.forEach {
        it.session.sendSerialized(
            sortUserRankPerQuestion(answerList[currentId])
        )
    }
}

private fun sortUserRankPerQuestion(answer: AnswerSocket?): AnswerSocket? {
    return if (answer != null) {
        val sortedAnswers = answer.answer.sortedWith(
            compareByDescending<UserAndAnswer> { it.answer }
                .thenBy { it.time }
        )
        answer.copy(answer = ArrayList(sortedAnswers))
    } else {
        null
    }
}

private fun sortUserTotalRankAtCurrentQuestion(answer: List<AnswerSocket?>?): List<AnswerSocket?>? {
    answer?.forEachIndexed { _, item ->
        item?.answer?.sortedWith(compareByDescending<UserAndAnswer> { it.answer }.thenBy { it.time })

        item?.answer?.forEachIndexed { index, userAndAnswer ->
            if (userAndAnswer.answer) {
                userAndAnswer.point = when (index) {
                    0 -> 3
                    1 -> 2
                    else -> 1
                }
            } else {
                userAndAnswer.point = 0
            }
        }
    }
    return answer
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
    val userName: String?,
    val userAvatar: String?,
    val time: String?,
    val answer: Boolean,
    var point: Int = 0
)