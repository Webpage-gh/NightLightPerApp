package com.example.nightlightperapp;

import android.os.IBinder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 探针模块：验证反射链路和护眼开关
 * 只做日志，不做任何实际操作
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 只在系统进程中 Hook
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] 已加载探针模块，Hook 系统进程");

        // Hook activityResumed
        XposedHelpers.findAndHookMethod(
                "com.android.server.wm.ActivityTaskManagerService",
                lpparam.classLoader,
                "activityResumed",
                IBinder.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            IBinder token = (IBinder) param.args[0];
                            String packageName = getPackageNameFromToken(token, lpparam.classLoader);
                            XposedBridge.log("[" + TAG + "] activityResumed: pkg=" + packageName);
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] activityResumed error: " + t.getMessage());
                        }
                    }
                }
        );

        // Hook activityPaused
        XposedHelpers.findAndHookMethod(
                "com.android.server.wm.ActivityTaskManagerService",
                lpparam.classLoader,
                "activityPaused",
                IBinder.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            IBinder token = (IBinder) param.args[0];
                            String packageName = getPackageNameFromToken(token, lpparam.classLoader);
                            XposedBridge.log("[" + TAG + "] activityPaused: pkg=" + packageName);
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] activityPaused error: " + t.getMessage());
                        }
                    }
                }
        );

        XposedBridge.log("[" + TAG + "] Hook 完成，请切换 App 测试");
    }

    /**
     * 从 activityToken 反推包名
     * 路径: token → ActivityRecord.forTokenLocked(token) → .packageName
     */
    private String getPackageNameFromToken(IBinder token, ClassLoader classLoader) {
        try {
            // 方式1: ActivityRecord.forTokenLocked(token)
            Class<?> activityRecordClass = Class.forName(
                    "com.android.server.wm.ActivityRecord",
                    false,
                    classLoader
            );

            Object activityRecord = XposedHelpers.callStaticMethod(
                    activityRecordClass,
                    "forTokenLocked",
                    token
            );

            if (activityRecord == null) {
                return "<null: forTokenLocked returned null>";
            }

            // 读取 packageName 字段
            String pkg = (String) XposedHelpers.getObjectField(activityRecord, "packageName");
            return pkg != null ? pkg : "<null: packageName field is null>";

        } catch (Throwable t1) {
            // 方式2: 直接反射 token 对象的类
            try {
                XposedBridge.log("[" + TAG + "] 方式1失败，尝试方式2: " + t1.getMessage());

                // token 的类名
                String tokenClassName = token.getClass().getName();
                XposedBridge.log("[" + TAG + "] token class: " + tokenClassName);

                // 尝试直接读 packageName 字段
                String pkg = (String) XposedHelpers.getObjectField(token, "packageName");
                return pkg != null ? pkg : "<null: direct field access returned null>";

            } catch (Throwable t2) {
                return "<error: " + t2.getMessage() + ">";
            }
        }
    }
}
