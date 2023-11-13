package cc.chenhe.qqnotifyevo.ui.common.permission

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cc.chenhe.qqnotifyevo.utils.getActivity

/**
 * If the time (ms) between requesting permission and being rejected is less than this threshold,
 * it may be permanently rejected.
 */
private const val ALWAYS_DENY_THRESHOLD = 200


/**
 * Creates a [MutablePermissionState] that is remembered across compositions.
 *
 * It's recommended that apps exercise the permissions workflow as described in the
 * [documentation](https://developer.android.com/training/permissions/requesting#workflow_for_requesting_permissions).
 *
 * @param permission the permission to control and observe.
 * @param onPermissionResult will be called with whether or not the user granted the permission
 *  after [PermissionState.launchPermissionRequest] is called.
 * @param onAlwaysDenied will be called if the user denied the permission and
 *  `shouldShowRationale=false` after [PermissionState.launchPermissionRequest] is called.
 *  It doesn't affect the calling of [onPermissionResult].
 * @param permissionChecker can custom the logic of permission checking.
 * @param alwaysRefreshPermissionStatus refresh the permission status, even if current status is
 *  [PermissionStatus.Granted]. Normally it is unnecessary because denying a permission triggers a
 *  process restart.
 */
@Composable
internal fun rememberMutablePermissionState(
    permission: String,
    onPermissionResult: (Boolean) -> Unit,
    onAlwaysDenied: () -> Unit,
    permissionChecker: ((permission: String) -> PermissionStatus)?,
    alwaysRefreshPermissionStatus: Boolean = false,
): MutablePermissionState {
    val ctx = LocalContext.current
    val inspectMode = LocalInspectionMode.current
    val permissionState = remember(permission, permissionChecker) {
        val activity = try {
            ctx.getActivity()
        } catch (e: IllegalStateException) {
            if (inspectMode) {
                null
            } else {
                throw e
            }
        }
        MutablePermissionState(permission, ctx, activity, permissionChecker)
    }

    // Refresh the permission status when the lifecycle is resumed
    PermissionLifecycleCheckerEffect(
        permissionState = permissionState,
        alwaysRefreshPermissionStatus = alwaysRefreshPermissionStatus
    )

    val launcher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) {
            permissionState.refreshPermissionStatus()
            if (!it && !(permissionState.status as PermissionStatus.Denied).shouldShowRationale
                && SystemClock.elapsedRealtime() - permissionState.launchTime < ALWAYS_DENY_THRESHOLD
            ) {
                onAlwaysDenied()
            }
            onPermissionResult(it)
        }
    DisposableEffect(permissionState, launcher) {
        permissionState.launcher = launcher
        onDispose {
            permissionState.launcher = null
        }
    }

    return permissionState
}

/**
 * Effect that updates the `hasPermission` state of a revoked [MutablePermissionState] permission
 * when the lifecycle gets called with [lifecycleEvent].
 *
 * @param alwaysRefreshPermissionStatus refresh the permission status, even if current status is
 *  [PermissionStatus.Granted]. Normally it is unnecessary because denying a permission triggers a
 *  process restart.
 */
@Composable
internal fun PermissionLifecycleCheckerEffect(
    permissionState: MutablePermissionState,
    lifecycleEvent: Lifecycle.Event = Lifecycle.Event.ON_RESUME,
    alwaysRefreshPermissionStatus: Boolean = false,
) {
    val observer = remember(permissionState) {
        LifecycleEventObserver { _, event ->
            if (event == lifecycleEvent) {
                // We don't check if the permission was denied as that triggers a process restart.
                if (alwaysRefreshPermissionStatus || permissionState.status != PermissionStatus.Granted) {
                    permissionState.refreshPermissionStatus()
                }
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(key1 = lifecycle, observer) {
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

/**
 * A mutable state object that can be used to control and observe permission status changes.
 *
 * In most cases, this will be created via [rememberMutablePermissionState].
 *
 * @param permission the permission to control and observe.
 * @param context to check the status of the [permission].
 * @param activity to check if the user should be presented with a rationale for [permission].
 *  should never be null unless in compose preview.
 * @param permissionChecker can custom the logic of permission checking.
 */
@Stable
internal class MutablePermissionState(
    override val permission: String,
    private val context: Context,
    private val activity: Activity?,
    private val permissionChecker: ((permission: String) -> PermissionStatus)?,
) : PermissionState {
    override var status: PermissionStatus by mutableStateOf(getPermissionStatus())

    internal var launcher: ActivityResultLauncher<String>? = null

    internal var launchTime: Long = 0
    override fun launchPermissionRequest() {
        launchTime = SystemClock.elapsedRealtime()
        launcher?.launch(permission)
            ?: throw IllegalStateException("ActivityResultLauncher cannot be null")
    }

    internal fun refreshPermissionStatus() {
        status = getPermissionStatus()
    }

    private fun getPermissionStatus(): PermissionStatus {
        if (permissionChecker != null) {
            return permissionChecker.invoke(permission)
        }
        val hasPermission =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) {
            PermissionStatus.Granted
        } else {
            if (activity == null) {
                PermissionStatus.Denied(false)
            } else {
                PermissionStatus.Denied(
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                )
            }
        }
    }
}