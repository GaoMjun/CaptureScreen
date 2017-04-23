package io.github.gaomjun.screencapture

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import io.github.gaomjun.screencapture.Muxer.MEDIA_TYPE
import java.nio.ByteBuffer

/**
 * Created by mannix on 4/23/17.
 */

data class SampleData(
        val buffer: ByteBuffer,
        val info: BufferInfo,
        val type: MEDIA_TYPE
)