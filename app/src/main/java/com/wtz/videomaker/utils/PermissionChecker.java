package com.wtz.videomaker.utils;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionChecker {
    private static final String TAG = PermissionChecker.class.getSimpleName();

    private static final int APP_OPS_MODE_ASK = 4;// "权限需要询问"

    private static final int OP_ERROR = -2;
    private static final int OP_NONE = -1;
    private static final int OP_COARSE_LOCATION = 0;
    private static final int OP_FINE_LOCATION = 1;
    private static final int OP_GPS = 2;
    private static final int OP_VIBRATE = 3;
    private static final int OP_READ_CONTACTS = 4;
    private static final int OP_WRITE_CONTACTS = 5;
    private static final int OP_READ_CALL_LOG = 6;
    private static final int OP_WRITE_CALL_LOG = 7;
    private static final int OP_READ_CALENDAR = 8;
    private static final int OP_WRITE_CALENDAR = 9;
    private static final int OP_WIFI_SCAN = 10;
    private static final int OP_POST_NOTIFICATION = 11;
    private static final int OP_NEIGHBORING_CELLS = 12;
    private static final int OP_CALL_PHONE = 13;
    private static final int OP_READ_SMS = 14;
    private static final int OP_WRITE_SMS = 15;
    private static final int OP_RECEIVE_SMS = 16;
    private static final int OP_RECEIVE_EMERGECY_SMS = 17;
    private static final int OP_RECEIVE_MMS = 18;
    private static final int OP_RECEIVE_WAP_PUSH = 19;
    private static final int OP_SEND_SMS = 20;
    private static final int OP_READ_ICC_SMS = 21;
    private static final int OP_WRITE_ICC_SMS = 22;
    private static final int OP_WRITE_SETTINGS = 23;
    private static final int OP_SYSTEM_ALERT_WINDOW = 24;
    private static final int OP_ACCESS_NOTIFICATIONS = 25;
    private static final int OP_CAMERA = 26;
    private static final int OP_RECORD_AUDIO = 27;
    private static final int OP_PLAY_AUDIO = 28;
    private static final int OP_READ_CLIPBOARD = 29;
    private static final int OP_WRITE_CLIPBOARD = 30;
    private static final int OP_TAKE_MEDIA_BUTTONS = 31;
    private static final int OP_TAKE_AUDIO_FOCUS = 32;
    private static final int OP_AUDIO_MASTER_VOLUME = 33;
    private static final int OP_AUDIO_VOICE_VOLUME = 34;
    private static final int OP_AUDIO_RING_VOLUME = 35;
    private static final int OP_AUDIO_MEDIA_VOLUME = 36;
    private static final int OP_AUDIO_ALARM_VOLUME = 37;
    private static final int OP_AUDIO_NOTIFICATION_VOLUME = 38;
    private static final int OP_AUDIO_BLUETOOTH_VOLUME = 39;
    private static final int OP_WAKE_LOCK = 40;
    private static final int OP_MONITOR_LOCATION = 41;
    private static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;
    private static final int OP_GET_USAGE_STATS = 43;
    private static final int OP_MUTE_MICROPHONE = 44;
    private static final int OP_TOAST_WINDOW = 45;
    private static final int OP_PROJECT_MEDIA = 46;
    private static final int OP_ACTIVATE_VPN = 47;
    private static final int OP_WRITE_WALLPAPER = 48;
    private static final int OP_ASSIST_STRUCTURE = 49;
    private static final int OP_ASSIST_SCREENSHOT = 50;
    private static final int OP_READ_PHONE_STATE = 51;
    private static final int OP_ADD_VOICEMAIL = 52;
    private static final int OP_USE_SIP = 53;
    private static final int OP_PROCESS_OUTGOING_CALLS = 54;
    private static final int OP_USE_FINGERPRINT = 55;
    private static final int OP_BODY_SENSORS = 56;
    private static final int OP_READ_CELL_BROADCASTS = 57;
    private static final int OP_MOCK_LOCATION = 58;
    private static final int OP_READ_EXTERNAL_STORAGE = 59;
    private static final int OP_WRITE_EXTERNAL_STORAGE = 60;
    private static final int OP_TURN_SCREEN_ON = 61;
    private static final int OP_GET_ACCOUNTS = 62;
    private static final int OP_RUN_IN_BACKGROUND = 63;
    private static final int OP_AUDIO_ACCESSIBILITY_VOLUME = 64;
    private static final int OP_READ_PHONE_NUMBERS = 65;
    private static final int OP_REQUEST_INSTALL_PACKAGES = 66;
    private static final int OP_PICTURE_IN_PICTURE = 67;
    private static final int OP_INSTANT_APP_START_FOREGROUND = 68;
    private static final int OP_ANSWER_PHONE_CALLS = 69;

    private static final Map<String, Integer> sOpPerms = new HashMap<>();

    static {
        sOpPerms.put(Manifest.permission.ACCESS_COARSE_LOCATION, OP_COARSE_LOCATION);
        sOpPerms.put(Manifest.permission.ACCESS_FINE_LOCATION, OP_COARSE_LOCATION);
        sOpPerms.put(Manifest.permission.VIBRATE, OP_VIBRATE);
        sOpPerms.put(Manifest.permission.READ_CONTACTS, OP_READ_CONTACTS);
        sOpPerms.put(Manifest.permission.WRITE_CONTACTS, OP_WRITE_CONTACTS);
        sOpPerms.put(Manifest.permission.READ_CALL_LOG, OP_READ_CALL_LOG);
        sOpPerms.put(Manifest.permission.WRITE_CALL_LOG, OP_WRITE_CALL_LOG);
        sOpPerms.put(Manifest.permission.READ_CALENDAR, OP_READ_CALENDAR);
        sOpPerms.put(Manifest.permission.WRITE_CALENDAR, OP_WRITE_CALENDAR);
        sOpPerms.put(Manifest.permission.ACCESS_WIFI_STATE, OP_COARSE_LOCATION);
        sOpPerms.put(Manifest.permission.CALL_PHONE, OP_CALL_PHONE);
        sOpPerms.put(Manifest.permission.READ_SMS, OP_READ_SMS);
        sOpPerms.put(Manifest.permission.RECEIVE_SMS, OP_RECEIVE_SMS);
        sOpPerms.put(Manifest.permission.RECEIVE_MMS, OP_RECEIVE_SMS);
        sOpPerms.put(Manifest.permission.RECEIVE_WAP_PUSH, OP_RECEIVE_SMS);
        sOpPerms.put(Manifest.permission.SEND_SMS, OP_SEND_SMS);
        sOpPerms.put(Manifest.permission.READ_SMS, OP_READ_SMS);
        sOpPerms.put(Manifest.permission.WRITE_SETTINGS, OP_WRITE_SETTINGS);
        sOpPerms.put(Manifest.permission.SYSTEM_ALERT_WINDOW, OP_SYSTEM_ALERT_WINDOW);
        sOpPerms.put(Manifest.permission.CAMERA, OP_CAMERA);
        sOpPerms.put(Manifest.permission.RECORD_AUDIO, OP_RECORD_AUDIO);
        sOpPerms.put(Manifest.permission.WAKE_LOCK, OP_WAKE_LOCK);
        sOpPerms.put(Manifest.permission.PACKAGE_USAGE_STATS, OP_GET_USAGE_STATS);
        sOpPerms.put(Manifest.permission.READ_PHONE_STATE, OP_READ_PHONE_STATE);
        sOpPerms.put(Manifest.permission.ADD_VOICEMAIL, OP_ADD_VOICEMAIL);
        sOpPerms.put(Manifest.permission.USE_SIP, OP_USE_SIP);
        sOpPerms.put(Manifest.permission.PROCESS_OUTGOING_CALLS, OP_PROCESS_OUTGOING_CALLS);
        sOpPerms.put(Manifest.permission.USE_FINGERPRINT, OP_USE_FINGERPRINT);
        sOpPerms.put(Manifest.permission.BODY_SENSORS, OP_BODY_SENSORS);
        sOpPerms.put(Manifest.permission.READ_EXTERNAL_STORAGE, OP_READ_EXTERNAL_STORAGE);
        sOpPerms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, OP_WRITE_EXTERNAL_STORAGE);
        sOpPerms.put(Manifest.permission.GET_ACCOUNTS, OP_GET_ACCOUNTS);
        sOpPerms.put(Manifest.permission.READ_PHONE_NUMBERS, OP_READ_PHONE_NUMBERS);
        sOpPerms.put(Manifest.permission.REQUEST_INSTALL_PACKAGES, OP_REQUEST_INSTALL_PACKAGES);
        sOpPerms.put(Manifest.permission.INSTANT_APP_FOREGROUND_SERVICE, OP_INSTANT_APP_START_FOREGROUND);
        sOpPerms.put(Manifest.permission.ANSWER_PHONE_CALLS, OP_ANSWER_PHONE_CALLS);
    }

    public enum PermissionState {
        MANIFEST_NOT_EXIST, USER_NOT_GRANTED, REQUESTING, ALLOWED, UNKNOWN
    }

    /**
     * 正常情况下，6.0以下版本在应用安装时就会默认授予权限；
     * 但是，有一些深度定制过的手机还是会提示用户该app申请了哪些权限，并且用户可以手动关闭。
     */
    public static PermissionState checkCommonPermission(Context context, String permission) {
        List<String> manifestPermissions = getManifestPermissions(context);
        if (manifestPermissions == null || !manifestPermissions.contains(permission)) {
            return PermissionState.MANIFEST_NOT_EXIST;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.M) {
            // 此版本情况下可能返回 PermissionState.UNKNOWN，后续业务需要做异常捕获处理
            return checkByAppOpsManager(context, permission);
        }

        PermissionState state;
        int result = context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid());
        if (result == PackageManager.PERMISSION_DENIED) {
            state = PermissionState.USER_NOT_GRANTED;
        } else {
            // 看样子是允许了，再进一步判断一下
            state = checkByAppOpsManager(context, permission);
            if (state == PermissionState.UNKNOWN) {
                // 在初步判断允许的前提下，进一步判断不出结果，那就认为是允许
                state = PermissionState.ALLOWED;
            }
        }

        return state;
    }

    public static PermissionState checkWriteSettingsPermission(Context context) {
        String permission = Manifest.permission.WRITE_SETTINGS;// Added in API level 1
        List<String> manifestPermissions = getManifestPermissions(context);
        if (manifestPermissions == null || !manifestPermissions.contains(permission)) {
            return PermissionState.MANIFEST_NOT_EXIST;
        }

        PermissionState state;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.M) {
            state = checkByAppOpsManager(context, permission);
        } else {
            state = Settings.System.canWrite(context) ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
        }

        return state;
    }

    public static PermissionState checkOverlayPermission(Context context) {
        String permission = Manifest.permission.SYSTEM_ALERT_WINDOW;// Added in API level 1
        List<String> manifestPermissions = getManifestPermissions(context);
        if (manifestPermissions == null || !manifestPermissions.contains(permission)) {
            return PermissionState.MANIFEST_NOT_EXIST;
        }

        PermissionState state;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.M) {
            state = checkByAppOpsManager(context, permission);
        } else {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1) {
                state = checkByAppOpsManager(context, permission);
            } else {
                state = Settings.canDrawOverlays(context) ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
            }
        }

        return state;
    }

    public static PermissionState checkInstallPackagesPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return PermissionState.ALLOWED;
        }

        String permission = Manifest.permission.REQUEST_INSTALL_PACKAGES;// Added in API level 23
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            List<String> manifestPermissions = getManifestPermissions(context);
            if (manifestPermissions == null || !manifestPermissions.contains(permission)) {
                return PermissionState.MANIFEST_NOT_EXIST;
            }
            return PermissionState.ALLOWED;
        }

        if (context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.O) {
            return checkByAppOpsManager(context, permission);
        }

        return context.getPackageManager().canRequestPackageInstalls() ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
    }

    public static PermissionState checkNotifyPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return PermissionState.ALLOWED;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return checkByAppOpsManager(context, OP_POST_NOTIFICATION);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // NotificationManager.areNotificationsEnabled: Added in API level 24
        return notificationManager.areNotificationsEnabled() ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
    }

    /**
     * 还有问题
     */
    public static PermissionState checkListenNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            String flat = Settings.Secure.getString(context.getContentResolver(),
                    "enabled_notification_listeners");
            return (flat != null && flat.contains(context.getPackageName())) ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
        }

        return checkByAppOpsManager(context, OP_ACCESS_NOTIFICATIONS);
    }

    private static List<String> getManifestPermissions(Context context) {
        List<String> permissions = null;
        PackageManager packageManager = context.getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
            if (packageInfo.requestedPermissions != null) {
                permissions = Arrays.asList(packageInfo.requestedPermissions);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return permissions;
    }

    private static PermissionState checkByAppOpsManager(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//            return PermissionState.UNKNOWN;
            return PermissionState.ALLOWED;
        }

        int opCode = permissionToOpCode(context, permission);
        Log.d(TAG, "checkByAppOpsManager " + permission + " opCode=" + opCode);

        return checkOpNoThrow(context, opCode);
    }

    private static PermissionState checkByAppOpsManager(Context context, int permissionCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
//            return PermissionState.UNKNOWN;
            return PermissionState.ALLOWED;
        }

        return checkOpNoThrow(context, permissionCode);
    }

    private static PermissionState checkOpNoThrow(Context context, int opCode) {
        if (opCode == OP_NONE) {
            //不需要管理的权限
            return PermissionState.ALLOWED;
        }

        PermissionState state = PermissionState.UNKNOWN;
        try {
            Object appOpsManagerObject = context.getSystemService(Context.APP_OPS_SERVICE);
            Class appOpsManagerClass = appOpsManagerObject.getClass();

            Class[] arrayOfClass = new Class[3];
            arrayOfClass[0] = Integer.TYPE;
            arrayOfClass[1] = Integer.TYPE;
            arrayOfClass[2] = String.class;
            Method checkOpMethod = appOpsManagerClass.getDeclaredMethod("checkOpNoThrow", arrayOfClass);
            checkOpMethod.setAccessible(true);

            Object[] arrayOfObject = new Object[3];
            arrayOfObject[0] = opCode;
            arrayOfObject[1] = Integer.valueOf(context.getApplicationInfo().uid);
            arrayOfObject[2] = context.getPackageName();
            int result = ((Integer) checkOpMethod.invoke(appOpsManagerObject, arrayOfObject)).intValue();
            Log.d(TAG, "checkOpNoThrow opCode " + opCode + " result=" + result);

            if (Build.MODEL.equals("MI 3") && Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT
                    && result == 3) {// 小米3的“询问”状态
                state = PermissionState.ALLOWED;
            } else {
                state = (result == AppOpsManager.MODE_ALLOWED || result == APP_OPS_MODE_ASK)
                        ? PermissionState.ALLOWED : PermissionState.USER_NOT_GRANTED;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return state;
    }

    /**
     * 6.0以下没有permissionToOpCode方法，且权限数量也不一样
     * 4.4.4 _NUM_OP = 43;
     * 5.0.0 _NUM_OP = 48;
     * 6.0.0 _NUM_OP = 63;
     *
     * @param permission
     * @return
     */
    private static int permissionToOpCode(Context context, String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (sOpPerms.containsKey(permission)) {
                return sOpPerms.get(permission);
            }
            return OP_NONE;
        }

        Object appOpsManagerObject = context.getSystemService(Context.APP_OPS_SERVICE);
        Class appOpsManagerClass = appOpsManagerObject.getClass();

        int permissionCode = OP_ERROR;
        try {
            Method permissionToOpMethod = appOpsManagerClass.getDeclaredMethod("permissionToOpCode", String.class);
            permissionToOpMethod.setAccessible(true);
            permissionCode = (Integer) permissionToOpMethod.invoke(appOpsManagerObject, permission);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return permissionCode;
    }

}
