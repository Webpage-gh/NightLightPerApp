package com.example.nightlightperapp;

import android.content.ContentResolver;
import android.os.IBinder;
import android.provider.Settings;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 阶段 2.5：硬编码相册，测试护眼开关实际效果
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";

    // 硬编码黑名单
    private static final String BLACKLISTED_PKG = "com.miui.gallery";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] 已加载 v2.5，黑名单: " + BLACKLISTED_PKG);

        String arClass = "com.android.server.wm.ActivityRecord";

        // Hook activityResumedLocked
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

                            if (BLACKLISTED_PKG.equals(pkg)) {
                                // 黑名单 App 到前台
                                ContentResolver cr = getSystemContentResolver();
                                if (cr != null && isNightLightOn(cr)) {
                                    disableNightLight(cr);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 关闭护眼");
                                } else {
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 护眼未开，跳过");
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] resumed error: " + t.getMessage());
                        }
                    }
                }
        );

        // Hook activityPaused
        XposedHelpers.findAndHookMethod(
                arClass,
                lpparam.classLoader,
                "activityPaused",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object activityRecord = param.thisObject;
                            String pkg = (String) XposedHelpers.getObjectField(activityRecord, "packageName");

                            if (BLACKLISTED_PKG.equals(pkg)) {
                                // 黑名单 App 离开前台
                                ContentResolver cr = getSystemContentResolver();
                                if (cr != null && !isNightLightOn(cr)) {
                                    enableNightLight(cr);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 离前台 → 恢复护眼");
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] paused error: " + t.getMessage());
                        }
                    }
                }
        );

        XposedBridge.log("[" + TAG + "] Hook 完成，请先开启系统护眼，再打开相册测试");
    }

    private boolean isNightLightOn(ContentResolver cr) {
        try {
            return Settings.System.getInt(cr, "screen_paper_mode_enabled") == 1;
        } catch (Settings.SettingNotFoundException e) {
            return false;
        }
    }

    private void disableNightLight(ContentResolver cr) {
        try {
            Settings.System.putInt(cr, "screen_paper_mode_enabled", 0);
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 关闭护眼失败: " + e.getMessage());
        }
    }

    private void enableNightLight(ContentResolver cr) {
        try {
            Settings.System.putInt(cr, "screen_paper_mode_enabled", 1);
        } catch (Exception e) {
            XposedBridge.log("[" + TAG + "] 恢复护眼失败: " + e.getMessage());
        }
    }

    private String getPackageNameFromToken(IBinder token, ClassLoader classLoader) {
        try {
            Class<?> arClass = Class.forName("com.android.server.wm.ActivityRecord", false, classLoader);
            Object ar = XposedHelpers.callStaticMethod(arClass, "forTokenLocked", token);
            if (ar == null) return "<null>";
            String pkg = (String) XposedHelpers.getObjectField(ar, "packageName");
            return pkg != null ? pkg : "<null pkg>";
        } catch (Throwable t) {
            return "<error>";
        }
    }

    private ContentResolver getSystemContentResolver() {
        try {
            Object at = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ActivityThread"),
                    "currentActivityThread"
            );
            Object ctx = XposedHelpers.callMethod(at, "getSystemContext");
            return ((android.content.Context) ctx).getContentResolver();
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] getSystemContentResolver 失败: " + t.getMessage());
            return null;
        }
    }
}
