package vn.patrick.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class Question(
    val questionContent: String,
    val questionImage: String
)

@Serializable
data class Answer(
    val answerText: String,
    val isAnswer: Boolean,
    val label: String
)

class QuestionService(private val database: Database) {
    object Questions : Table("question_table") {
        val id = integer("id").autoIncrement()

        @Suppress("unused")
        val startDate = datetime("date_created").defaultExpression(CurrentDateTime)
        val questionContent = varchar("question_text", length = 5000)
        val questionImage = varchar("question_image", length = 5000)
        override val primaryKey = PrimaryKey(id)
    }

    object Answers : Table("answer_table") {
        val id = integer("id").autoIncrement()
        val questionId = integer("question_id")
        val answerText = varchar("answer_text", length = 5000)
        val isAnswer = bool("is_answer")
        val label = varchar("label", length = 10)
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

    suspend fun createQuestion(question: Question): Int = dbQuery {
        Questions.insert {
            it[questionContent] = question.questionContent
            it[questionImage] = question.questionImage
        }[Questions.id]
    }

    suspend fun createAnswer(answer: Answer, id: Int): Int = dbQuery {
        Answers.insert {
            it[questionId] =
                Questions.select { Questions.id eq id }.single()[Questions.id]
            it[label] = answer.label
            it[answerText] = answer.answerText
            it[isAnswer] = answer.isAnswer
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
            list.add(QuestionResult(t, u.map { AnswerWithLabel(it.label, it.answerText, it.isAnswer!!) }))
        }
        Results(list)
    }

    suspend fun getQuestionByDate(): List<QuestionResult> = dbQuery {
        val data = ArrayList<QuestionResult>()
        Questions.join(
            otherTable = Answers,
            joinType = JoinType.INNER,
            additionalConstraint = {
                Questions.id eq Answers.questionId
            }
        ).selectAll().map {
            Result(
                it[Questions.questionContent],
                it[Answers.label],
                it[Answers.answerText],
                it[Answers.isAnswer]
            )
        }.groupBy {
            it.questionContent
        }.forEach { (t, u) ->
            data.add(QuestionResult(t, u.map {
                AnswerWithLabel(it.label, it.answerText, it.isAnswer!!)
            }))
        }
        data
    }
}

@Serializable
data class Result(
    val questionContent: String,
    val label: String,
    val answerText: String,
    val isAnswer: Boolean? = false,
)

@Serializable
data class QuestionResult(
    val question: String,
    val answer: List<AnswerWithLabel>
)

@Serializable
data class Data(
    val data: QuestionResult?
)

@Serializable
data class DataText(
    val result: String? = null,
    val progress: String? = null
)

@Serializable
data class AnswerWithLabel(
    val label: String,
    val answer: String,
    var isAnswer: Boolean?
)

@Serializable
data class Results(
    val data: List<QuestionResult>?
)