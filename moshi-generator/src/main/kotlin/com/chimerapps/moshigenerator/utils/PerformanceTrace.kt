package com.chimerapps.moshigenerator.utils

import com.chimerapps.moshigenerator.SimpleLogger

/**
 * @author nicolaverbeeck
 */
var tracePerformance = false
var depth = 0

inline fun <T> tracePerformance(log: SimpleLogger, name: String, block: () -> T): T {
    val start = System.nanoTime()
    ++depth
    try {
        return block()
    } finally {
        --depth
        val end = System.nanoTime()

        if (tracePerformance) {
            val time = (end - start)
            log.logInfo("${depthPrefix()}==PERFORMANCE== `$name` took ${time / 1000000.0} msec")
        }
    }
}

fun depthPrefix(): String {
    return buildString {
        for (i in 0 until depth) {
            append("  ")
        }
    }
}