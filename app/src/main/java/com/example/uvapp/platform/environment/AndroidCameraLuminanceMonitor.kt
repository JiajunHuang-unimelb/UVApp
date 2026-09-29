package com.example.uvapp.platform.environment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.uvapp.domain.environment.CameraLuminanceClassifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Lifecycle-bound CameraX analysis that publishes relative luminance without storing frames. */
class AndroidCameraLuminanceMonitor(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    @Volatile private var enabled = false

    fun start(lifecycleOwner: LifecycleOwner) {
        if (enabled) return
        if (
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AndroidEnvironmentContextProvider.updateCameraLuminance(null)
            return
        }

        enabled = true
        val providerFuture = ProcessCameraProvider.getInstance(applicationContext)
        providerFuture.addListener(
            {
                if (!enabled) return@addListener
                try {
                    val provider = providerFuture.get()
                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                    analysis.setAnalyzer(analysisExecutor, LuminanceAnalyzer())
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                    cameraProvider = provider
                    imageAnalysis = analysis
                } catch (_: Exception) {
                    enabled = false
                    AndroidEnvironmentContextProvider.updateCameraLuminance(null)
                }
            },
            ContextCompat.getMainExecutor(applicationContext),
        )
    }

    fun stop() {
        enabled = false
        imageAnalysis?.clearAnalyzer()
        imageAnalysis?.let { cameraProvider?.unbind(it) }
        imageAnalysis = null
        cameraProvider = null
        AndroidEnvironmentContextProvider.updateCameraLuminance(null)
    }

    private class LuminanceAnalyzer : ImageAnalysis.Analyzer {
        private var lastPublishedTimestampNanos = Long.MIN_VALUE

        override fun analyze(image: ImageProxy) {
            try {
                val timestamp = image.imageInfo.timestamp
                if (
                    lastPublishedTimestampNanos != Long.MIN_VALUE &&
                    timestamp - lastPublishedTimestampNanos < PUBLISH_INTERVAL_NANOS
                ) {
                    return
                }
                val plane = image.planes.firstOrNull() ?: return
                val reading =
                    CameraLuminanceClassifier.sampleYPlane(
                        buffer = plane.buffer,
                        width = image.width,
                        height = image.height,
                        rowStride = plane.rowStride,
                        pixelStride = plane.pixelStride,
                    )
                if (reading != null) {
                    lastPublishedTimestampNanos = timestamp
                    AndroidEnvironmentContextProvider.updateCameraLuminance(reading)
                }
            } finally {
                image.close()
            }
        }

        private companion object {
            const val PUBLISH_INTERVAL_NANOS = 1_000_000_000L
        }
    }
}
