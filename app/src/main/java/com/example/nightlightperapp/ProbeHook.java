package com.example.nightlightperapp;

import android.content.ContentResolver;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

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
                                // 黑名单 App 到前台
                                int saved = Settings.System.getIntForUser(cr, SAVED_KEY, SENTINEL_NONE, UserHandle.USER_CURRENT);
                                if (saved == SENTINEL_NONE) {
                                    int current = Settings.System.getIntForUser(cr, PAPER_MODE_KEY, 0, UserHandle.USER_CURRENT);
                                    Settings.System.putIntForUser(cr, SAVED_KEY, current, UserHandle.USER_CURRENT);
                                    Settings.System.putIntForUser(cr, PAPER_MODE_KEY, 0, UserHandle.USER_CURRENT);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 保存原值=" + current + "，关闭护眼");
                                } else {
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 已接管，跳过");
                                }
                            } else {
                                // 非黑名单 App 到前台
                                int saved = Settings.System.getIntForUser(cr, SAVED_KEY, SENTINEL_NONE, UserHandle.USER_CURRENT);
                                if (saved != SENTINEL_NONE) {
                                    Settings.System.putIntForUser(cr, PAPER_MODE_KEY, saved, UserHandle.USER_CURRENT);
                                    Settings.System.putIntForUser(cr, SAVED_KEY, SENTINEL_NONE, UserHandle.USER_CURRENT);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 恢复护眼=" + saved);
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] resumed error: " + Log.getStackTraceString(t));
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
            XposedBridge.log("[" + TAG + "] getPackageNameFromToken: " + Log.getStackTraceString(t));
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
            XposedBridge.log("[" + TAG + "] getSystemContentResolver: " + Log.getStackTraceString(t));
            return null;
        }
    }
}
