package dev.infyplus.floorpin.data.db

import app.cash.sqldelight.db.SqlDriver
import dev.infyplus.floorpin.db.FloorPinDb
import kotlinx.serialization.json.Json

/** Platform-provided SQLDelight driver (Android needs a Context; iOS doesn't). */
expect class DriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DriverFactory): FloorPinDb = FloorPinDb(factory.create())

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}
