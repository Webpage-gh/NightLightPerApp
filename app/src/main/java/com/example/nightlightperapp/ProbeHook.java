package com.example.nightlightperapp;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 阶段 2.6：黑名单从 ContentProvider 读取
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";
    private static final String PAPER_MODE_KEY = "screen_paper_mode_enabled";
    private static final String SAVED_KEY = "night_light_perapp_saved";
    private static final int SENTINEL_NONE = -1;
    private static final Uri BLACKLIST_URI = Uri.parse("content://com.example.nightlightperapp.blacklist/blacklist");

    private volatile Set<String> mBlacklist = new HashSet<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] 已加载 v2.6");

        // 加载黑名单
        refreshBlacklist();
        XposedBridge.log("[" + TAG + "] 初始黑名单: " + mBlacklist);

        String arClass = "com.android.server.wm.ActivityRecord";

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

                            if (mBlacklist.contains(pkg)) {
                                // 黑名单 App 到前台
                                int saved = Settings.System.getInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved == SENTINEL_NONE) {
                                    int current = Settings.System.getInt(cr, PAPER_MODE_KEY, 0);
                                    Settings.System.putInt(cr, SAVED_KEY, current);
                                    Settings.System.putInt(cr, PAPER_MODE_KEY, 0);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 保存原值=" + current + "，关闭护眼");
                                }
                            } else {
                                // 非黑名单 App 到前台
                                int saved = Settings.System.getInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved != SENTINEL_NONE) {
                                    Settings.System.putInt(cr, PAPER_MODE_KEY, saved);
                                    Settings.System.putInt(cr, SAVED_KEY, SENTINEL_NONE);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 恢复护眼=" + saved);
                                }
                            }

                            // 每次都刷新黑名单（简单实现，后续可优化）
                            refreshBlacklist();

                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] error: " + Log.getStackTraceString(t));
                        }
                    }
                }
        );

        XposedBridge.log("[" + TAG + "] Hook 完成");
    }

    private void refreshBlacklist() {
        try {
            ContentResolver cr = getSystemContentResolver();
            if (cr == null) return;

            Bundle result = cr.call(BLACKLIST_URI, BlacklistProvider.METHOD_GET_BLACKLIST, null, null);
            if (result != null) {
                java.util.ArrayList<String> list = result.getStringArrayList(BlacklistProvider.KEY_PACKAGES);
                if (list != null) {
                    mBlacklist = new HashSet<>(list);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] refreshBlacklist: " + Log.getStackTraceString(t));
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
            return ((Context) ctx).getContentResolver();
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] getSystemContentResolver: " + Log.getStackTraceString(t));
            return null;
        }
    }
}
