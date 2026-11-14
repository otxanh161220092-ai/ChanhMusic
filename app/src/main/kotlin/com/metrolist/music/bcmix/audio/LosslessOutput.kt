package com.metrolist.music.bcmix.audio

/**
 * Lossless and bit-perfect output. NOT PORTED: BitChord's USB and output sink
 * are not in this build, so no output is ever lossless-capable here.
 */
object LosslessOutput {
    val capable: Boolean = false
}
