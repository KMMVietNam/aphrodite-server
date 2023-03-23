package vn.patrick.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class Question(
    val questionContent: String,
    val questionNumber: Int,
    val image: String
)

@Serializable
data class Answer(
    val answerContent: String,
    val questionNumber: Int,
    val label: String
)

class QuestionService(private val database: Database) {
    object Questions : Table("question_table") {
        val id = integer("id").autoIncrement()
        val questionContent = varchar("questionText", length = 5000)
        val questionNumber = integer("questionNumber")
        val image = varchar("contentImage", length = 5000)
        override val primaryKey = PrimaryKey(id)
    }

    object Answers : Table("answer_table") {
        val id = integer("id").autoIncrement()
        val questionId = integer("questionId")
        val answerText = varchar("answerText", length = 5000)
        val label = varchar("label", length = 50)
        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Questions)
        }
    }

    init {
        transaction(database) {
            SchemaUtils.create(Answers)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun createQuestion(question1: Question): Int = dbQuery {
        Questions.insert {
            it[questionContent] = question1.questionContent
            it[questionNumber] = question1.questionNumber
            it[image] = question1.image
        }[Questions.id]
    }

    suspend fun createAnswer(answer: Answer): Int = dbQuery {
        Answers.insert {
            it[questionId] =
                Questions.select { Questions.questionNumber eq answer.questionNumber }.single()[Questions.id]
            it[answerText] = answer.answerContent
            it[label] = answer.label
        }[Answers.id]
    }

    suspend fun getAll(): Results = dbQuery {
        val res = Questions.join(Answers, JoinType.INNER, additionalConstraint = {
            Questions.id eq Answers.questionId
        })
        val results = res.selectAll().map {
            Result(it[Questions.questionContent], it[Answers.label], it[Answers.answerText])
        }
        val byContent = results.groupBy { it.questionContent }
        val list = ArrayList<QuestionResult>()
        byContent.forEach { (t, u) ->
            list.add(QuestionResult(t, u.map { AnswerWithLabel(it.label, it.answerText) }))
        }
        Results(list)
    }
}

@Serializable
data class Result(
    val questionContent: String,
    val label: String,
    val answerText: String
)

@Serializable
data class QuestionResult(
    val question: String,
    val answer: List<AnswerWithLabel>
)

@Serializable
data class AnswerWithLabel(
    val label: String,
    val answer: String
)

@Serializable
data class Results(
    val data: List<QuestionResult>?
)