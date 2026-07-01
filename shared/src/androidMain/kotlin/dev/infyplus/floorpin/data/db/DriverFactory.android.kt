package dev.infyplus.floorpin.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import dev.infyplus.floorpin.db.FloorPinDb

actual class DriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(FloorPinDb.Schema, context, "floorpin.db")
}
