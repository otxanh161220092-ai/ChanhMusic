/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT of a fixed power-of-two [size]. */
internal class Fft(val size: Int) {
    private val cosTable = DoubleArray(size / 2) { cos(2.0 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { -sin(2.0 * PI * it / size) }
    private val reversed = IntArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two: $size" }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Forward transform, X[k] = sum x[n] * e^(-2 pi i k n / N). */
    fun forward(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val odd = j + half
                    val xr = re[odd] * wr - im[odd] * wi
                    val xi = re[odd] * wi + im[odd] * wr
                    re[odd] = re[j] - xr
                    im[odd] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += length
            }
            length = length shl 1
        }
    }
}
