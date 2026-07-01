package dev.infyplus.floorpin.data

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Epoch millis. */
expect fun nowMillis(): Long

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()
