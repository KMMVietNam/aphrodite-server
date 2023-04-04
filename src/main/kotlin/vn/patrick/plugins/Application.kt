package vn.patrick.plugins

import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.jetbrains.exposed.sql.Database

fun main(args: Array<String>): Unit = EngineMain.main(args)


fun Application.module() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val driverClassName = environment.config.property("storage.driverClassName").getString()
    val jdbcURL = environment.config.property("storage.jdbcURL").getString()
    val database = Database.connect(
        url = jdbcURL,
        driver = driverClassName
    )
    configureWebSocket(database)
    configureAuthentication()
    configureSerialization()
    configureDatabases(
        database,
        audience,
        issuer,
        secret
    )
    configureRouting(
        database,
        audience,
        issuer,
        secret
    )
}
