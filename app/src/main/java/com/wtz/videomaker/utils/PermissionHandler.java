package com.wtz.videomaker.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.wtz.videomaker.R;
import com.wtz.videomaker.utils.PermissionChecker.PermissionState;

/**
 * 部分参考：https://blog.csdn.net/yanzhenjie1003/article/details/52503533/
 */
public class PermissionHandler {
    private static final String TAG = PermissionHandler.class.getSimpleName();

    private static final int REQUEST_CODE_PERMISSIONS = 1000;
    private static final int REQUEST_CODE_COMMON_STATIC_PERMISSION = 1001;
    private static final int REQUEST_CODE_WRITE_SETTINGS = 1002;
    private static final int REQUEST_CODE_OVERLAY = 1003;
    private static final int REQUEST_CODE_INSTALL = 1004;

    private static final int REQUEST_CODE_NOTIFICATION = 1005;
    public static final String CUSTOM_PERMISSION_NOTIFICATION = "CUSTOM_PERMISSION_NOTIFICATION";
    private static final int REQUEST_CODE_NOTIFICATION_LISTENER = 1006;
    public static final String CUSTOM_PERMISSION_NOTIFICATION_LISTENER = "CUSTOM_PERMISSION_NOTIFICATION_LISTENER";

    /**
     * 无法使用6.0以上动态申请的方式获取权限的普通权限
     */
    private String mCurrentCommonStaticPermission;

    public interface PermissionHandleListener {
        void onPermissionResult(String permission, PermissionState state);
    }

    private Activity mActivity;
    private PermissionHandleListener mHandleResult;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public PermissionHandler(Activity activity, PermissionHandleListener handler) {
        mActivity = activity;
        mHandleResult = handler;
    }

    public void destroy() {
        mHandler.removeCallbacksAndMessages(null);
    }

    public void handleCommonPermission(String permission) {
        PermissionState state = PermissionChecker.checkCommonPermission(mActivity, permission);

        if (state == PermissionState.USER_NOT_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && mActivity.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.M) {
                if (canShowRationale(mActivity, permission)) {
                            // 先弹窗解释，同意后再申请权限
                    showRationale(mActivity, permission, mHandleResult);
                } else {
                    // 直接请求权限
                    mHandleResult.onPermissionResult(permission, PermissionState.REQUESTING);
                    requestCommonPermission(mActivity, permission);
                }
            } else {
                showRationale(mActivity, permission, mHandleResult);
            }

            return;
        }

        mHandleResult.onPermissionResult(permission, state);
    }

    private void requestCommonPermission(Activity activity, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && mActivity.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[]{permission}, REQUEST_CODE_PERMISSIONS);
        } else {
            mCurrentCommonStaticPermission = permission;
            Intent intent = AppPermSettingsIntent.getDetailSettings(mActivity);
            mActivity.startActivityForResult(intent, REQUEST_CODE_COMMON_STATIC_PERMISSION);
        }
    }

    public void handleWriteSettingsPermission() {
        PermissionState state = PermissionChecker.checkWriteSettingsPermission(mActivity);

        if (state == PermissionState.USER_NOT_GRANTED) {
            showRationale(mActivity, Manifest.permission.WRITE_SETTINGS, mHandleResult);
            return;
        }

        mHandleResult.onPermissionResult(Manifest.permission.WRITE_SETTINGS, state);
    }

    private void requestWriteSettingsPermission() {
        Intent intent = AppPermSettingsIntent.getWriteSettings(mActivity);
        mActivity.startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
    }

    public void handleOverlayPermission() {
        PermissionState state = PermissionChecker.checkOverlayPermission(mActivity);

        if (state == PermissionState.USER_NOT_GRANTED) {
            showRationale(mActivity, Manifest.permission.SYSTEM_ALERT_WINDOW, mHandleResult);
            return;
        }

        mHandleResult.onPermissionResult(Manifest.permission.SYSTEM_ALERT_WINDOW, state);
    }

    private void requestOverlayPermission() {
        Intent intent = AppPermSettingsIntent.getOverlaySettings(mActivity);
        mActivity.startActivityForResult(intent, REQUEST_CODE_OVERLAY);
    }

    public void handleInstallPackagesPermission() {
        PermissionState state = PermissionChecker.checkInstallPackagesPermission(mActivity);

        if (state == PermissionState.USER_NOT_GRANTED) {
            showRationale(mActivity, Manifest.permission.REQUEST_INSTALL_PACKAGES, mHandleResult);
            return;
        }

        mHandleResult.onPermissionResult(Manifest.permission.REQUEST_INSTALL_PACKAGES, state);
    }

    private void requestInstallPackagesPermission() {
        Intent intent = AppPermSettingsIntent.getInstallSettings(mActivity);
        mActivity.startActivityForResult(intent, REQUEST_CODE_INSTALL);
    }

    public void handleNotifyPermission() {
        PermissionState state = PermissionChecker.checkNotifyPermission(mActivity);

        if (state == PermissionState.USER_NOT_GRANTED) {
            showRationale(mActivity, CUSTOM_PERMISSION_NOTIFICATION, mHandleResult);
            return;
        }

        mHandleResult.onPermissionResult(CUSTOM_PERMISSION_NOTIFICATION, state);
    }

    private void requestNotifyPermission() {
        Intent intent = AppNotifySettingsIntent.getNotifySettings(mActivity);
        mActivity.startActivityForResult(intent, REQUEST_CODE_NOTIFICATION);
    }

    /**
     * 还有问题
     */
    public void handleListenNotificationPermission() {
        PermissionState state = PermissionChecker.checkListenNotificationPermission(mActivity);

        if (state == PermissionState.USER_NOT_GRANTED) {
            showRationale(mActivity, CUSTOM_PERMISSION_NOTIFICATION_LISTENER, mHandleResult);
            return;
        }

        mHandleResult.onPermissionResult(CUSTOM_PERMISSION_NOTIFICATION_LISTENER, state);
    }

    private void requestListenNotificationPermission() {
        Intent intent = AppNotifySettingsIntent.getListenNotifySettings();
        mActivity.startActivityForResult(intent, REQUEST_CODE_NOTIFICATION_LISTENER);
    }

    private static boolean canShowRationale(Activity activity, String permission) {
        // 判断是否应该向用户解释：
        // 如果应用从来没有申请这个权限的话，返回false
        // 如果应用之前请求过此权限但用户拒绝了请求，此方法将返回 true。
        // 如果用户在过去拒绝了权限请求，并选择了 Don't ask again 选项，此方法将返回 false。
        // 如果设备规范禁止应用具有该权限，此方法也会返回 false。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return activity.shouldShowRequestPermissionRationale(permission);
        }
        return false;
    }

    private void showRationale(final Activity activity, final String permission, final PermissionHandleListener handleResult) {
        handleResult.onPermissionResult(permission, PermissionState.REQUESTING);
        String name = transformPermissionText(activity, permission);
        String message = activity.getString(R.string.message_permission_rationale,
                "\n" + name);

        new AlertDialog.Builder(activity).setCancelable(false)
                .setTitle(R.string.permission_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.permission_dialog_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (Manifest.permission.WRITE_SETTINGS.equals(permission)) {
                            requestWriteSettingsPermission();
                        } else if (Manifest.permission.SYSTEM_ALERT_WINDOW.equals(permission)) {
                            requestOverlayPermission();
                        } else if (Manifest.permission.REQUEST_INSTALL_PACKAGES.equals(permission)) {
                            requestInstallPackagesPermission();
                        } else if (CUSTOM_PERMISSION_NOTIFICATION.equals(permission)) {
                            requestNotifyPermission();
                        } else if (CUSTOM_PERMISSION_NOTIFICATION_LISTENER.equals(permission)) {
                            requestListenNotificationPermission();
                        } else {
                            requestCommonPermission(activity, permission);
                        }
                    }
                })
                .setNegativeButton(R.string.permission_dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleResult.onPermissionResult(permission, PermissionState.USER_NOT_GRANTED);
                    }
                })
                .show();
    }

    private static String transformPermissionText(Context context, String permission) {
        String message = permission;
        switch (permission) {
            case Manifest.permission.READ_CALENDAR:
            case Manifest.permission.WRITE_CALENDAR: {
                message = context.getString(R.string.permission_name_calendar);
                break;
            }

            case Manifest.permission.CAMERA: {
                message = context.getString(R.string.permission_name_camera);
                break;
            }
            case Manifest.permission.READ_CONTACTS:
            case Manifest.permission.WRITE_CONTACTS: {
                message = context.getString(R.string.permission_name_contacts);
                break;
            }
            case Manifest.permission.GET_ACCOUNTS: {
                message = context.getString(R.string.permission_name_accounts);
                break;
            }
            case Manifest.permission.ACCESS_FINE_LOCATION:
            case Manifest.permission.ACCESS_COARSE_LOCATION: {
                message = context.getString(R.string.permission_name_location);
                break;
            }
            case Manifest.permission.RECORD_AUDIO: {
                message = context.getString(R.string.permission_name_microphone);
                break;
            }
            case Manifest.permission.READ_PHONE_STATE:
            case Manifest.permission.CALL_PHONE:
            case Manifest.permission.READ_CALL_LOG:
            case Manifest.permission.WRITE_CALL_LOG:
            case Manifest.permission.ADD_VOICEMAIL:
            case Manifest.permission.USE_SIP:
            case Manifest.permission.PROCESS_OUTGOING_CALLS:
            case Manifest.permission.READ_PHONE_NUMBERS:
            case Manifest.permission.ANSWER_PHONE_CALLS: {
                message = context.getString(R.string.permission_name_phone);
                break;
            }
            case Manifest.permission.BODY_SENSORS: {
                message = context.getString(R.string.permission_name_sensors);
                break;
            }
            case Manifest.permission.SEND_SMS:
            case Manifest.permission.RECEIVE_SMS:
            case Manifest.permission.READ_SMS:
            case Manifest.permission.RECEIVE_WAP_PUSH:
            case Manifest.permission.RECEIVE_MMS: {
                message = context.getString(R.string.permission_name_sms);
                break;
            }
            case Manifest.permission.READ_EXTERNAL_STORAGE:
            case Manifest.permission.WRITE_EXTERNAL_STORAGE: {
                message = context.getString(R.string.permission_name_storage);
                break;
            }
            case Manifest.permission.WRITE_SETTINGS:
                message = context.getString(R.string.permission_name_write_settings);
                break;
            case Manifest.permission.SYSTEM_ALERT_WINDOW:
                message = context.getString(R.string.permission_name_system_alert_window);
                break;
            case Manifest.permission.REQUEST_INSTALL_PACKAGES:
                message = context.getString(R.string.permission_name_install_packages);
                break;
            case CUSTOM_PERMISSION_NOTIFICATION:
                message = context.getString(R.string.permission_name_send_notification);
                break;
            case CUSTOM_PERMISSION_NOTIFICATION_LISTENER:
                message = context.getString(R.string.permission_name_access_notification);
                break;
        }
        return message;
    }

    private static class AppPermSettingsIntent {

        public static Intent getDetailSettings(Context context) {
            return get(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        }

        public static Intent getWriteSettings(Context context) {
            return get(context, Settings.ACTION_MANAGE_WRITE_SETTINGS);
        }

        public static Intent getOverlaySettings(Context context) {
            return get(context, Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        }

        public static Intent getInstallSettings(Context context) {
            return get(context, Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        }

        private static Intent get(Context context, String action) {
            Intent intent = null;
            String vendor = Build.MANUFACTURER.toLowerCase();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                if (vendor.equals("xiaomi")) {
                    intent = xiaomi(context);
                } else if (vendor.equals("huawei")) {
                    intent = huawei(context);
                } else if (vendor.equals("oppo")) {
                    intent = oppo(context);
                } else if (vendor.equals("vivo")) {
                    intent = vivo(context);
                } else if (vendor.equals("meizu")) {
                    intent = meizu(context);
                } else {
                    intent = defaultApi(context);
                }
            } else {
                if (vendor.equals("meizu")) {
                    intent = meizu(context);
                } else {
                    intent = new Intent(action);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                }
            }
            return intent;
        }

        private static Intent xiaomi(Context context) {
            Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.putExtra("extra_pkgname", context.getPackageName());
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent huawei(Context context) {
            Intent intent = new Intent();
            intent.setClassName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity");
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity");
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.huawei.systemmanager", "com.huawei.notificationmanager.ui.NotificationManagmentActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent oppo(Context context) {
            Intent intent = new Intent();
            intent.putExtra("packageName", context.getPackageName());
            intent.setClassName("com.color.safecenter",
                    "com.color.safecenter.permission.floatwindow.FloatWindowListActivity");
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.coloros.safecenter", "com.coloros.safecenter.sysfloatwindow.FloatWindowListActivity");
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.oppo.safe", "com.oppo.safe.permission.PermissionAppListActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent vivo(Context context) {
            Intent intent = new Intent();
            intent.setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.FloatWindowManager");
            intent.putExtra("packagename", context.getPackageName());
            if (hasActivity(context, intent)) return intent;

            intent.setClassName("com.iqoo.secure", "com.iqoo.secure.safeguard.SoftPermissionDetailActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent meizu(Context context) {
            Intent intent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
            intent.putExtra("packageName", context.getPackageName());
            intent.setClassName("com.meizu.safe", "com.meizu.safe.security.AppSecActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent defaultApi(Context context) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);// Added in API level 9
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            return intent;
        }
    }

    private static class AppNotifySettingsIntent {

        public static Intent getListenNotifySettings() {
            return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        }

        public static Intent getNotifySettings(Context context) {
            Intent intent = null;
            String vendor = Build.MANUFACTURER.toLowerCase();
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                // <= api 20
                if (vendor.equals("xiaomi")) {
                    intent = xiaomi(context);
                } else if (vendor.equals("huawei")) {
                    intent = huawei(context);
                } else if (vendor.equals("oppo")) {
                    intent = oppo(context);
                } else if (vendor.equals("vivo")) {
                    intent = vivo(context);
                } else if (vendor.equals("meizu")) {
                    intent = meizu(context);
                } else {
                    intent = defaultApi(context);
                }
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // api 21-25
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra("app_package", context.getPackageName());
                intent.putExtra("app_uid", context.getApplicationInfo().uid);
            } else {
                // >= api 26
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.getApplicationInfo().uid);
            }
            return intent;
        }

        private static Intent xiaomi(Context context) {
            // TODO: 2019/7/5
            return defaultApi(context);
        }

        private static Intent huawei(Context context) {
            Intent intent = new Intent();
            intent.setClassName("com.huawei.systemmanager", "com.huawei.notificationmanager.ui.NotificationManagmentActivity");
            if (hasActivity(context, intent)) return intent;

            return defaultApi(context);
        }

        private static Intent oppo(Context context) {
            // TODO: 2019/7/5
            return defaultApi(context);
        }

        private static Intent vivo(Context context) {
            // TODO: 2019/7/5
            return defaultApi(context);
        }

        private static Intent meizu(Context context) {
            // TODO: 2019/7/5
            return defaultApi(context);
        }

        private static Intent defaultApi(Context context) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);// Added in API level 9
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            return intent;
        }
    }

    private static boolean hasActivity(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    public void handleActivityRequestPermissionsResult(int requestCode,
                                                       String[] permissions, int[] grantResults) {
        Log.d(TAG, "handleActivityRequestPermissionsResult requestCode=" + requestCode);
        switch (requestCode) {
            case REQUEST_CODE_PERMISSIONS:
                for (int i = 0; i < grantResults.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "Permission " + permissions[i] + " is not granted.");
                        if (canShowRationale(mActivity, permissions[i])) {
                            // 再次弹窗解释，同意后再申请权限
                            showRationale(mActivity, permissions[i], mHandleResult);
                        } else {
                            // 已经禁止
                            mHandleResult.onPermissionResult(permissions[i], PermissionState.USER_NOT_GRANTED);
                        }
                    } else {
                        // 已经同意
                        mHandleResult.onPermissionResult(permissions[i], PermissionState.ALLOWED);
                    }
                }
                break;
            default:
                break;
        }
    }

    public void handleActivityResult(int requestCode) {
        Log.d(TAG, "handleActivityResult requestCode=" + requestCode);
        PermissionState state;
        switch (requestCode) {
            case REQUEST_CODE_COMMON_STATIC_PERMISSION:
                state = PermissionChecker.checkCommonPermission(mActivity, mCurrentCommonStaticPermission);
                Log.d(TAG, "Permission " + mCurrentCommonStaticPermission + " state is " + state);
                mHandleResult.onPermissionResult(mCurrentCommonStaticPermission, state);
                break;
            case REQUEST_CODE_WRITE_SETTINGS:
                state = PermissionChecker.checkWriteSettingsPermission(mActivity);
                Log.d(TAG, "Permission WRITE_SETTINGS state is " + state);
                mHandleResult.onPermissionResult(Manifest.permission.WRITE_SETTINGS, state);
                break;
            case REQUEST_CODE_OVERLAY:
                state = PermissionChecker.checkOverlayPermission(mActivity);
                Log.d(TAG, "Permission SYSTEM_ALERT_WINDOW state is " + state);
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                    // android8.0在用户同意后会先返回不允许，但过一会儿就会返回允许
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            PermissionState delayState = PermissionChecker.checkOverlayPermission(mActivity);
                            Log.d(TAG, "after delay Permission SYSTEM_ALERT_WINDOW state is " + delayState);
                            mHandleResult.onPermissionResult(Manifest.permission.SYSTEM_ALERT_WINDOW, delayState);
                        }
                    }, 500);
                } else {
                    mHandleResult.onPermissionResult(Manifest.permission.SYSTEM_ALERT_WINDOW, state);
                }
                break;
            case REQUEST_CODE_INSTALL:
                state = PermissionChecker.checkInstallPackagesPermission(mActivity);
                Log.d(TAG, "Permission REQUEST_INSTALL_PACKAGES state is " + state);
                mHandleResult.onPermissionResult(Manifest.permission.REQUEST_INSTALL_PACKAGES, state);
                break;
            case REQUEST_CODE_NOTIFICATION:
                state = PermissionChecker.checkNotifyPermission(mActivity);
                Log.d(TAG, "Permission NOTIFICATION state is " + state);
                mHandleResult.onPermissionResult(CUSTOM_PERMISSION_NOTIFICATION, state);
                break;
            case REQUEST_CODE_NOTIFICATION_LISTENER:
                state = PermissionChecker.checkListenNotificationPermission(mActivity);
                Log.d(TAG, "Permission NOTIFICATION_LISTENER state is " + state);
                mHandleResult.onPermissionResult(CUSTOM_PERMISSION_NOTIFICATION_LISTENER, state);
                break;
        }
    }

}
