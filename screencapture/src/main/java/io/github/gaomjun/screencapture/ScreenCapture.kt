package io.github.gaomjun.screencapture

import android.app.Activity
import android.content.Context
import android.content.Context.MEDIA_PROJECTION_SERVICE
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Created by mannix on 4/23/17.
 */
class ScreenCapture(val context: Context) {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: VideoEncoder? = null
    private var muxer: Muxer? = null
    private var path: String? = null

    var width: Int = 1080
    var height: Int = 1920
    var dpi: Int = 2

    companion object {
        val SCREEN_CAPTURE_REQUEST_CODE = 1000;
    }

    fun start(path: String) {
        this.path = path

        mediaProjectionManager = context.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val screenCaptureIntent = mediaProjectionManager?.createScreenCaptureIntent()
        if (context is Activity) {
            context.startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_REQUEST_CODE)
        }
    }

    fun stop() {
        mediaProjection?.stop()
        videoEncoder?.stop()
        muxer?.stop()

        startMuxingVideo = false
        hasKeyFrame = false
    }

    fun userAgreeCaptureScreenCallback(resultCode: Int, data: Intent?) {
        videoEncoder = VideoEncoder(width, height)
        videoEncoder?.callback = VideoEncoderCallback()
        videoEncoder?.start()

        muxer = Muxer()

        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data);
        mediaProjection?.createVirtualDisplay("Screen", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, videoEncoder?.inputSurface, null, null)
    }

    private inner class VideoEncoderCallback: VideoEncoder.Callback {
        override fun onOutputBufferAvailable(buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {
            super.onOutputBufferAvailable(buffer, info)

            println("${info?.size} ${info?.presentationTimeUs}")

            if (!hasKeyFrame && info?.flags == BUFFER_FLAG_KEY_FRAME)
                hasKeyFrame = true

            if (startMuxingVideo && hasKeyFrame)
                muxer?.muxing(buffer!!, info!!, Muxer.MEDIA_TYPE.MEDIA_TYPE_VIDEO)
        }

        override fun onOutputFormatChanged(format: MediaFormat?) {
            super.onOutputFormatChanged(format)

            println("onOutputFormatChanged")

            muxer?.videoFormat = format
            muxer?.start(path!!)
            startMuxingVideo = true
        }
    }

    private var startMuxingVideo = false
    private var hasKeyFrame = false
}