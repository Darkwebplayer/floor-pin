package dev.infyplus.floorpin.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.infyplus.floorpin.db.FloorPinDb

actual class DriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(FloorPinDb.Schema, "floorpin.db")
}
