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
import rx.Observable
import rx.Subscription
import rx.observables.ConnectableObservable
import rx.subjects.PublishSubject
import java.nio.ByteBuffer

/**
 * Created by mannix on 4/23/17.
 */
class ScreenCapture(val context: Context) {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null

    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null

    private var muxer: Muxer? = null
    private var path: String? = null

    var width: Int = 1080
    var height: Int = 1920
    var dpi: Int = 2

    private var videoFormatObservable: PublishSubject<MediaFormat>? = null
    private var audioFormatObservable: PublishSubject<MediaFormat>? = null

    companion object {
        val SCREEN_CAPTURE_REQUEST_CODE = 1000
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
        audioEncoder?.stop()
        muxer?.stop()

        startMuxing = false
        hasKeyFrame = false
    }

    fun userAgreeCaptureScreenCallback(resultCode: Int, data: Intent?) {
        videoFormatObservable = PublishSubject.create<MediaFormat>()
        audioFormatObservable = PublishSubject.create<MediaFormat>()

        videoEncoder = VideoEncoder(width, height)
        videoEncoder?.callback = VideoEncoderCallback()
        videoEncoder?.start()

        audioEncoder = AudioEncoder()
        audioEncoder?.callback = AudioEncoderCallback()
        audioEncoder?.start()

        muxer = Muxer()
        Observable.zip(videoFormatObservable, audioFormatObservable, {videoFormat, audioFormat ->
            if (videoFormat != null && audioFormat != null) {
                println("video and audio format prepared")
//                muxer?.videoFormat = videoFormat
//                muxer?.audioFormat = audioFormat
//                muxer?.start(path!!)
                startMuxing = true

            }
        }).subscribe()

        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        mediaProjection?.createVirtualDisplay("Screen", width, height, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, videoEncoder?.inputSurface, null, null)
    }

    private var startMuxing = false
    private var hasKeyFrame = false
    private inner class VideoEncoderCallback: VideoEncoder.Callback {
        override fun onOutputBufferAvailable(buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {
            super.onOutputBufferAvailable(buffer, info)

            if (!startMuxing) return

            if (!hasKeyFrame && info?.flags == BUFFER_FLAG_KEY_FRAME) {
                hasKeyFrame = true
            }

            if (hasKeyFrame) {
                println("videoBuffer ${info?.size} ${info?.presentationTimeUs}")
            }
//            if (startMuxingVideo && hasKeyFrame)
//                muxer?.muxing(buffer!!, info!!, Muxer.MEDIA_TYPE.MEDIA_TYPE_VIDEO)
        }

        override fun onOutputFormatChanged(format: MediaFormat?) {
            super.onOutputFormatChanged(format)

            println("video onOutputFormatChanged")

            videoFormatObservable?.onNext(format)
            videoFormatObservable?.onCompleted()
        }
    }

    private inner class AudioEncoderCallback : AudioEncoder.Callback {
        override fun onOutputBufferAvailable(buffer: ByteBuffer?, info: MediaCodec.BufferInfo?) {
            super.onOutputBufferAvailable(buffer, info)

            if (!startMuxing) return

            if (hasKeyFrame) {
                println("audioBuffer ${info?.size} ${info?.presentationTimeUs}")
            }

        }

        override fun onOutputFormatChanged(format: MediaFormat?) {
            super.onOutputFormatChanged(format)

            println("audio onOutputFormatChanged")

            audioFormatObservable?.onNext(format)
            audioFormatObservable?.onCompleted()
        }
    }
}
