package com.roundcutter.model

import java.util.UUID

data class Clip(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs
}
