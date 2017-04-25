package io.github.gaomjun.screencapture

import android.media.MediaCodec
import android.media.MediaCodec.*
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.*
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import io.github.gaomjun.recorder.AudioConfiguration
import io.github.gaomjun.recorder.PCMCapture
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Created by qq on 24/4/2017.
 */
class AudioEncoder : PCMCapture.PCMDataCallback {
    private var codec: MediaCodec? = null

    private var encodingThread: HandlerThread? = null
    private var encodingThreadHandler: Handler? = null

    private var pcmCapture: PCMCapture? = null

    var saveAACToFile = false
    private var bufferedOutputStream: BufferedOutputStream? = null

    private var SAMPLE_RATE = 44100
    private var CHANNELS = 2
    private var BITRATE = 192000
    private var MAX_INPUT_SIZE = 14208

    constructor() {
        pcmCapture = PCMCapture()
    }

    constructor(audioConfiguration: AudioConfiguration) {
        pcmCapture = PCMCapture(audioConfiguration)

        SAMPLE_RATE = audioConfiguration.SAMPLE_RATE
        CHANNELS = audioConfiguration.CHANNEL_NUM
        BITRATE = audioConfiguration.BITRATE
        MAX_INPUT_SIZE = audioConfiguration.MAX_INPUT_SIZE
    }

    init {
        initEncoder()
    }

    private fun initEncoder() {
        val format = createAudioFormat(MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
        format.setInteger(KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(KEY_SAMPLE_RATE, SAMPLE_RATE)
        format.setInteger(KEY_CHANNEL_COUNT, CHANNELS)
        format.setInteger(KEY_BIT_RATE, BITRATE)
        format.setInteger(KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)

        codec = createEncoderByType(MIMETYPE_AUDIO_AAC)
        codec?.configure(format, null, null, CONFIGURE_FLAG_ENCODE)

        codec?.start()
    }

    fun start() {
        println("AudioEncoder start")
        encodingThread = HandlerThread("encodingThread")
        encodingThread?.start()
        encodingThreadHandler = Handler(encodingThread?.looper)

        pcmCapture?.savePCMToFile = saveAACToFile
        pcmCapture?.pcmDataCallback = this
        pcmCapture?.start()
    }

    fun stop() {
        println("AudioEncoder stop")
        pcmCapture?.pcmDataCallback = null
        pcmCapture?.stop()

        if (encodingThread != null) {
            val moribund = encodingThread
            encodingThread = null
            moribund!!.interrupt()
        }
    }

    var audioDataListener: AudioDataListener? = null
    interface AudioDataListener {
        fun onPCMData(data: ByteArray, size: Int, timestamp: Long) {}
        fun onAACData(buffer: ByteBuffer, info: BufferInfo) {}
    }

    var callback: Callback? = null
    interface Callback {
        fun onOutputBufferAvailable(buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {}
        fun onOutputFormatChanged(format: MediaFormat?) {}
    }

    private inner class EncodingRunnable(val data: ByteArray, val size: Int, val timestamp: Long) : Runnable {
        override fun run() {

            val inputBufferIndex = codec?.dequeueInputBuffer(10000)

            if (inputBufferIndex!! >= 0) {
                val inputBuffer = codec?.getInputBuffer(inputBufferIndex)
                inputBuffer?.position(0)
                inputBuffer?.put(data, 0, size)

                codec?.queueInputBuffer(inputBufferIndex, 0, data.size, timestamp, 0)
            }

            val info = BufferInfo()
            val outputBufferIndex = codec?.dequeueOutputBuffer(info, 0)
            if (outputBufferIndex == INFO_OUTPUT_FORMAT_CHANGED) {
                callback?.onOutputFormatChanged(codec?.outputFormat)
            }
            if (outputBufferIndex!! >= 0) {
                info.presentationTimeUs = timestamp
                val buffer = codec?.getOutputBuffer(outputBufferIndex)!!
                audioDataListener?.onAACData(buffer, info)
                callback?.onOutputBufferAvailable(buffer, info)

                if (saveAACToFile) {
                    if (bufferedOutputStream == null) {
                        val f = File(Environment.getExternalStorageDirectory(), "DCIM/Camera/audio.aac")
                        if (f.exists()) {
                            f.delete()
                            println("rm " + f.absolutePath)
                        }
                        bufferedOutputStream = BufferedOutputStream(FileOutputStream(f))
                    }

                    val aacData = ByteArray(info.size)
                    buffer.get(aacData)
                    bufferedOutputStream?.write(addADTStoPacket(aacData.size))
                    bufferedOutputStream?.write(aacData)
                }

                codec?.releaseOutputBuffer(outputBufferIndex, false)
            }
        }
    }

    override fun onPCMData(data: ByteArray, size: Int, timestamp: Long) {
//        println("onPCMData $size")

        encodingThreadHandler?.post(EncodingRunnable(data, size, timestamp))
        audioDataListener?.onPCMData(data, size, timestamp)
    }

    companion object {
        // https://wiki.multimedia.cx/index.php/ADTS
        private fun addADTStoPacket(dataLength: Int): ByteArray {
            val packetLen = dataLength + 7

            val packet = ByteArray(7)

            val profile = 2  //AAC LC
            //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
            val freqIdx = 4  //44.1KHz
            val chanCfg = 2  //CPE

            // fill in ADTS data
            packet[0] = 0xFF.toByte()
            packet[1] = 0xF9.toByte()
            packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
            packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
            packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
            packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
            packet[6] = 0xFC.toByte()

            return packet
        }
    }
}