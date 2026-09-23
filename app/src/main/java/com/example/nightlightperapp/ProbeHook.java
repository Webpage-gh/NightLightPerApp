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
 * 阶段 2.5：硬编码相册 + 哨兵值
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";
    private static final String BLACKLISTED_PKG = "com.miui.gallery";
    private static final String PAPER_MODE_KEY = "screen_paper_mode_enabled";
    private static final String SAVED_KEY = "night_light_perapp_saved";
    private static final int SENTINEL_NONE = -1; // 未接管

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
                            ContentResolver cr = getSystemContentResolver();
                            if (cr == null) return;

                            if (BLACKLISTED_PKG.equals(pkg)) {
                                int saved = Settings.System.getInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved == SENTINEL_NONE) {
                                    // 未接管，先保存原值，再关护眼
                                    int current = Settings.System.getInt(cr, PAPER_MODE_KEY, 0);
                                    Settings.System.putInt(cr, SAVED_KEY, current);
                                    Settings.System.putInt(cr, PAPER_MODE_KEY, 0);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 保存原值=" + current + "，关闭护眼");
                                } else {
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 已接管，跳过");
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
                            ContentResolver cr = getSystemContentResolver();
                            if (cr == null) return;

                            if (BLACKLISTED_PKG.equals(pkg)) {
                                int saved = Settings.System.getInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved != SENTINEL_NONE) {
                                    // 已接管，恢复原值，复位哨兵
                                    Settings.System.putInt(cr, PAPER_MODE_KEY, saved);
                                    Settings.System.putInt(cr, SAVED_KEY, SENTINEL_NONE);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 离前台 → 恢复护眼=" + saved);
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
