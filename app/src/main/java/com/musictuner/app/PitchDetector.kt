package com.musictuner.app

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class NoteInfo(val name: String, val octave: Int, val cents: Int)

object PitchDetector {
    private val NAMES = arrayOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B"
    )

    // Возвращает частоту в Гц или -1, если звука нет
    fun detect(buf: ShortArray, size: Int, sampleRate: Int): Float {
        var sum = 0.0
        for (i in 0 until size) sum += buf[i].toDouble() * buf[i]
        if (sqrt(sum / size) < 500) return -1f

        val minLag = sampleRate / 1500
        val maxLag = sampleRate / 60
        val n = size - maxLag
        if (n <= 0) return -1f

        val corr = DoubleArray(maxLag + 2)
        var max = 0.0
        for (lag in minLag..maxLag) {
            var s = 0.0
            for (i in 0 until n) s += buf[i].toDouble() * buf[i + lag]
            corr[lag] = s
            if (s > max) max = s
        }
        if (max <= 0) return -1f

        for (lag in (minLag + 1) until maxLag) {
            if (corr[lag] > 0.9 * max &&
                corr[lag] >= corr[lag - 1] &&
                corr[lag] >= corr[lag + 1]
            ) {
                val a = corr[lag - 1]
                val b = corr[lag]
                val c = corr[lag + 1]
                val d = a - 2 * b + c
                val shift = if (d != 0.0) 0.5 * (a - c) / d else 0.0
                return (sampleRate / (lag + shift)).toFloat()
            }
        }
        return -1f
    }

    fun noteInfo(freq: Float, refA: Float, transpose: Int): NoteInfo {
        val midi = 69.0 + 12.0 * ln(freq / refA.toDouble()) / ln(2.0)
        val rounded = midi.roundToInt()
        val cents = ((midi - rounded) * 100).roundToInt()
        val written = rounded + transpose
        val name = NAMES[((written % 12) + 12) % 12]
        return NoteInfo(name, written / 12 - 1, cents)
    }
}
