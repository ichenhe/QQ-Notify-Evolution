package cc.chenhe.qqnotifyevo.ui.common.permission

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.utils.getActivity

/**
 * Creates a [PermissionState] that is remembered across compositions.
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
 */
@Composable
fun rememberPermissionState(
    permission: String,
    onPermissionResult: (Boolean) -> Unit = {},
    onAlwaysDenied: () -> Unit = {},
    permissionChecker: ((permission: String) -> PermissionStatus)? = null
): PermissionState = rememberMutablePermissionState(
    permission = permission,
    onPermissionResult = onPermissionResult,
    onAlwaysDenied = onAlwaysDenied,
    permissionChecker = permissionChecker,
)

/**
 * Similar to [rememberPermissionState], but supports api level that below 33. If the system doesn't
 * support [Manifest.permission.POST_NOTIFICATIONS] permission, [NotificationManagerCompat.areNotificationsEnabled]
 * is used to check the permission status, and [PermissionStatus.Denied.shouldShowRationale] is always `false`.
 *
 * **Warning:** do not call [PermissionState.launchPermissionRequest] if api level is lower than 33.
 * You must implement your own logic instead, typically it should be like:
 * ```
 * val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
 *     putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
 * }
 * context.startActivity(intent)
 * ```
 * The returned [PermissionState] will be updated automatically, just like the one returned by
 * [rememberPermissionState], regardless of the api level.
 */
@SuppressLint("InlinedApi")
@Composable
fun rememberNotificationPermissionState(
    onPermissionResult: (Boolean) -> Unit = {},
    onAlwaysDenied: () -> Unit = {},
): PermissionState {
    val inspectMode = LocalInspectionMode.current
    val ctx = LocalContext.current
    return rememberMutablePermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        onPermissionResult = onPermissionResult,
        onAlwaysDenied = onAlwaysDenied,
        alwaysRefreshPermissionStatus = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
        permissionChecker = { p ->
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(ctx).areNotificationsEnabled()
            }
            if (hasPermission) {
                PermissionStatus.Granted
            } else {
                val shouldShowRationale =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            ctx.getActivity()
                        } catch (e: IllegalStateException) {
                            if (inspectMode) {
                                null
                            } else {
                                throw e
                            }
                        }?.let { aty ->
                            ActivityCompat.shouldShowRequestPermissionRationale(aty, p)
                        } ?: false
                    } else {
                        false
                    }
                PermissionStatus.Denied(shouldShowRationale)
            }
        },
    )
}

@Stable
interface PermissionState {
    /**
     * The permission to control and observe.
     */
    val permission: String


    /**
     * [permission]'s status
     */
    var status: PermissionStatus

    val isGranted: Boolean get() = this.status == PermissionStatus.Granted

    /**
     * Request the [permission] to the user.
     *
     * This should always be triggered from non-composable scope, for example, from a side-effect
     * or a non-composable callback. Otherwise, this will result in an IllegalStateException.
     *
     * This triggers a system dialog that asks the user to grant or revoke the permission.
     * Note that this dialog might not appear on the screen if the user doesn't want to be asked
     * again or has denied the permission multiple times.
     * This behavior varies depending on the Android level API.
     */
    fun launchPermissionRequest()
}

@Stable
sealed interface PermissionStatus {
    data object Granted : PermissionStatus
    data class Denied(
        val shouldShowRationale: Boolean
    ) : PermissionStatus
}
