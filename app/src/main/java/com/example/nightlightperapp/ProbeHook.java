package com.example.nightlightperapp;

import android.os.IBinder;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 探针模块 v3：Hook ActivityRecord 的实际方法
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] 已加载探针模块 v3");

        String arClass = "com.android.server.wm.ActivityRecord";

        // Hook activityResumedLocked(IBinder, boolean)
        try {
            XposedHelpers.findAndHookMethod(
                    arClass,
                    lpparam.classLoader,
                    "activityResumedLocked",
                    IBinder.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                IBinder token = (IBinder) param.args[0];
                                String pkg = getPackageNameFromToken(token, lpparam.classLoader);
                                XposedBridge.log("[" + TAG + "] activityResumed: pkg=" + pkg);
                            } catch (Throwable t) {
                                XposedBridge.log("[" + TAG + "] activityResumed error: " + t.getMessage());
                            }
                        }
                    }
            );
            XposedBridge.log("[" + TAG + "] ✅ Hook activityResumedLocked 成功");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] ❌ Hook activityResumedLocked 失败: " + t.getMessage());
        }

        // Hook activityPaused(boolean)
        try {
            XposedHelpers.findAndHookMethod(
                    arClass,
                    lpparam.classLoader,
                    "activityPaused",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                // thisObject 就是 ActivityRecord 实例
                                Object activityRecord = param.thisObject;
                                String pkg = (String) XposedHelpers.getObjectField(activityRecord, "packageName");
                                XposedBridge.log("[" + TAG + "] activityPaused: pkg=" + pkg);
                            } catch (Throwable t) {
                                XposedBridge.log("[" + TAG + "] activityPaused error: " + t.getMessage());
                            }
                        }
                    }
            );
            XposedBridge.log("[" + TAG + "] ✅ Hook activityPaused 成功");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] ❌ Hook activityPaused 失败: " + t.getMessage());
        }

        XposedBridge.log("[" + TAG + "] 探测完成，请切换 App 后查看日志");
    }

    /**
     * 从 activityToken 反推包名
     */
    private String getPackageNameFromToken(IBinder token, ClassLoader classLoader) {
        try {
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
                return "<null>";
            }

            String pkg = (String) XposedHelpers.getObjectField(activityRecord, "packageName");
            return pkg != null ? pkg : "<null pkg>";

        } catch (Throwable t) {
            return "<error: " + t.getMessage() + ">";
        }
    }
}
