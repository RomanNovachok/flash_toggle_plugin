package com.example.flash_toggle_plugin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class FlashTogglePlugin :
    FlutterPlugin,
    MethodCallHandler,
    ActivityAware,
    PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val permissionRequestCode = 9007
    }

    private lateinit var channel: MethodChannel
    private var cameraManager: CameraManager? = null
    private var flashCameraId: String? = null
    private var isTorchEnabled = false
    private var activity: Activity? = null
    private var activityBinding: ActivityPluginBinding? = null
    private var pendingPermissionResult: Result? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashCameraId) {
                isTorchEnabled = enabled
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flash_toggle_plugin/methods")
        channel.setMethodCallHandler(this)

        cameraManager =
            binding.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        flashCameraId = findFlashCameraId(cameraManager)
        cameraManager?.registerTorchCallback(torchCallback, null)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "toggleLight" -> toggleLight(result)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        cameraManager?.unregisterTorchCallback(torchCallback)
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        activityBinding = binding
        binding.addRequestPermissionsResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        detachFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        detachFromActivity()
    }

    private fun toggleLight(result: Result) {
        val cameraId = flashCameraId

        if (cameraId == null) {
            result.error("NO_FLASH", "No flashlight-capable camera was found.", null)
            return
        }

        if (!hasCameraPermission()) {
            requestCameraPermission(result)
            return
        }

        val targetState = !isTorchEnabled

        try {
            cameraManager?.setTorchMode(cameraId, targetState)
            isTorchEnabled = targetState
            result.success(isTorchEnabled)
        } catch (error: CameraAccessException) {
            result.error("FLASH_ERROR", error.message, null)
        } catch (error: SecurityException) {
            result.error("FLASH_PERMISSION", error.message, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != permissionRequestCode) {
            return false
        }

        val result = pendingPermissionResult
        pendingPermissionResult = null

        if (grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (result != null) {
                toggleLight(result)
            }
        } else {
            result?.error("FLASH_PERMISSION", "Camera permission was denied.", null)
        }

        return true
    }

    private fun findFlashCameraId(manager: CameraManager?): String? {
        val currentManager = manager ?: return null

        return currentManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = currentManager.getCameraCharacteristics(cameraId)
            val hasFlash =
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun hasCameraPermission(): Boolean {
        val currentActivity = activity ?: return false
        return ContextCompat.checkSelfPermission(
            currentActivity,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(result: Result) {
        val currentActivity = activity

        if (currentActivity == null) {
            result.error("NO_ACTIVITY", "No foreground activity is available.", null)
            return
        }

        if (pendingPermissionResult != null) {
            result.error(
                "PERMISSION_IN_PROGRESS",
                "Camera permission request is already running.",
                null,
            )
            return
        }

        pendingPermissionResult = result
        ActivityCompat.requestPermissions(
            currentActivity,
            arrayOf(Manifest.permission.CAMERA),
            permissionRequestCode,
        )
    }

    private fun detachFromActivity() {
        activityBinding?.removeRequestPermissionsResultListener(this)
        activityBinding = null
        activity = null
    }
}
