package io.github.gaomjun.screencapture

import android.media.MediaCodec
import android.media.MediaCodec.CONFIGURE_FLAG_ENCODE
import android.media.MediaCodec.createEncoderByType
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities.*
import android.media.MediaFormat
import android.media.MediaFormat.*
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Created by mannix on 4/23/17.
 */
class VideoEncoder(val width: Int, val height: Int) {

    private var codec: MediaCodec? = null

    var inputSurface: Surface? = null
        private set

    init {
        initEncoder()
    }

    fun start() {
        println("VideoEncoder start")
        codec?.start()
    }

    fun stop() {
        println("VideoEncoder stop")
        codec?.stop()
        codec?.release()
    }

    private fun initEncoder() {
        val format = createVideoFormat(MIMETYPE_VIDEO_HEVC, width, height)
        format.setInteger(KEY_COLOR_FORMAT, COLOR_FormatSurface)
        format.setInteger(KEY_BIT_RATE, width * height * 3 * 4)
        format.setInteger(KEY_FRAME_RATE, 60)
        format.setInteger(KEY_I_FRAME_INTERVAL, 1)

        codec = createEncoderByType(format.getString(KEY_MIME))
        codec?.setCallback(EncoderCallback())
        codec?.configure(format, null, null, CONFIGURE_FLAG_ENCODE)

        inputSurface = codec?.createInputSurface()
    }

    private inner class EncoderCallback : MediaCodec.Callback() {
        override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: MediaCodec.BufferInfo?) {
//            println("onOutputBufferAvailable $index ${info?.flags} ${info?.size} ${info?.presentationTimeUs}")

            callback?.onOutputBufferAvailable(codec?.getOutputBuffer(index), info)

            codec?.releaseOutputBuffer(index, false)
        }
        override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
//            println("onOutputFormatChanged")

            callback?.onOutputFormatChanged(format)
        }
        override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
            println("onInputBufferAvailable")
        }
        override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
            println("onError")
        }
    }

    var callback: Callback? = null

    interface Callback {
        fun onOutputBufferAvailable(buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {}
        fun onOutputFormatChanged(format: MediaFormat?) {}
    }
}