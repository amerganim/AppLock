package com.amerganim.lockapp

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * Captures and stores "intruder selfies" — a front-camera photo taken after too
 * many wrong unlock attempts. Photos live in private internal storage and are
 * named "<timestampMillis>_<package>.jpg".
 */
object IntruderManager {

    private const val TAG = "IntruderManager"

    private fun dir(context: Context): File =
        File(context.filesDir, "intruders").apply { mkdirs() }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Silently capture a front-camera photo. [owner] must be a resumed
     * Activity/LifecycleOwner that also provides the [Context].
     */
    fun <T> capture(owner: T, blamedPackage: String) where T : Context, T : LifecycleOwner {
        if (!hasCameraPermission(owner)) return
        val future = ProcessCameraProvider.getInstance(owner)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
            } catch (e: Exception) {
                Log.w(TAG, "Could not bind front camera", e)
                return@addListener
            }
            val pkg = blamedPackage.replace('/', '.')
            val file = File(dir(owner), "${System.currentTimeMillis()}_$pkg.jpg")
            val options = ImageCapture.OutputFileOptions.Builder(file).build()
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(owner),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        runCatching { provider.unbindAll() }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "Capture failed", exception)
                        runCatching { provider.unbindAll() }
                    }
                }
            )
        }, ContextCompat.getMainExecutor(owner))
    }

    /** Captured photos, newest first. */
    fun list(context: Context): List<IntruderPhoto> =
        dir(context).listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedByDescending { it.name }
            ?.map { IntruderPhoto(it, parseTime(it), parsePackage(it)) }
            ?: emptyList()

    fun clearAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun parseTime(file: File): Long =
        file.nameWithoutExtension.substringBefore('_').toLongOrNull() ?: file.lastModified()

    private fun parsePackage(file: File): String =
        file.nameWithoutExtension.substringAfter('_', "")
}

data class IntruderPhoto(val file: File, val timeMillis: Long, val packageName: String)
