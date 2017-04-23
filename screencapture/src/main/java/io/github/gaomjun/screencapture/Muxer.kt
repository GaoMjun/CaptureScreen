package io.github.gaomjun.screencapture

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import java.nio.ByteBuffer

/**
 * Created by mannix on 4/23/17.
 */
class Muxer {

    private var muxer: MediaMuxer? = null
    private var path: String? = null

    var videoFormat: MediaFormat? = null
    var audioFormat: MediaFormat? = null

    private var videoTrackIndex: Int? = null
    private var audioTrackIndex: Int? = null

    private var muxingThread: HandlerThread? = null
    private var muxingThreadHandler: MuxingThreadHandler? = null

    init {
        muxingThread = HandlerThread("muxingThread")
        muxingThread?.start()
        muxingThreadHandler = MuxingThreadHandler(muxingThread?.looper)
    }

    fun start(path: String) {
        this.path = path

        muxingThreadHandler?.removeMessages(WHAT_START)
        muxingThreadHandler?.sendMessage(muxingThreadHandler?.obtainMessage(WHAT_START))
    }

    fun stop() {
        muxingThreadHandler?.removeMessages(WHAT_STOP)
        muxingThreadHandler?.sendMessage(muxingThreadHandler?.obtainMessage(WHAT_STOP))
    }

    fun muxing(buffer: ByteBuffer, info: BufferInfo, type: MEDIA_TYPE) {
        muxingThreadHandler?.removeMessages(WHAT_MUXING)
        muxingThreadHandler?.sendMessage(muxingThreadHandler?.obtainMessage(WHAT_MUXING, SampleData(buffer, info, type)))
    }

    private fun initMuxer() {
        if (videoFormat == null && audioFormat == null)
            throw Exception("muxer need at least one audio or video track")

        muxer = MediaMuxer(path, MUXER_OUTPUT_MPEG_4)

        if (videoFormat != null)
            videoTrackIndex = muxer?.addTrack(videoFormat)

        if (audioFormat != null)
            audioTrackIndex = muxer?.addTrack(audioFormat)

        muxer?.start()
    }

    private inner class MuxingThreadHandler(looper: Looper?) : Handler(looper) {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                WHAT_START -> {
                    initMuxer()
                }

                WHAT_STOP -> {
                    muxer?.stop()
                    muxer?.release()

                    videoFormat = null
                    audioFormat = null

                    videoTrackIndex = null
                    audioTrackIndex = null
                }

                WHAT_MUXING -> {
                    val obj = msg.obj
                    if (obj is SampleData) {
                        val buffer = obj.buffer
                        val info = obj.info
                        val type = obj.type

                        when (type) {
                            MEDIA_TYPE.MEDIA_TYPE_VIDEO -> {
                                muxer?.writeSampleData(videoTrackIndex!!, buffer, info)
                            }

                            MEDIA_TYPE.MEDIA_TYPE_AUDIO -> {
                                muxer?.writeSampleData(audioTrackIndex!!, buffer, info)
                            }
                        }
                    }
                }
            }
        }

    }

    companion object {
        private val WHAT_START = 0x0001
        private val WHAT_STOP = 0x0010
        private val WHAT_MUXING = 0x0100
    }

    enum class MEDIA_TYPE {
        MEDIA_TYPE_VIDEO,
        MEDIA_TYPE_AUDIO
    }
}