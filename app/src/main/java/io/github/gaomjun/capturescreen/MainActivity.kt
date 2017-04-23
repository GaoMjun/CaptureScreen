package io.github.gaomjun.capturescreen

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import io.github.gaomjun.extensions.postDelayedR
import io.github.gaomjun.screencapture.ScreenCapture
import java.io.File

class MainActivity : Activity() {

    private var screenCapture: ScreenCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        screenCapture = ScreenCapture(this)

        Handler().postDelayedR(1000) {
            val path = "/sdcard/DCIM/Ringo/video.mp4"
            val f = File(path)
            if (f.exists()) f.delete()

            screenCapture?.start(path)
        }

        Handler().postDelayedR(100000) {
            screenCapture?.stop()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ScreenCapture.SCREEN_CAPTURE_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    screenCapture?.userAgreeCaptureScreenCallback(resultCode, data)
                }
            }
        }
    }
}
