package com.example.nightlightperapp;

import android.content.ContentResolver;
import android.content.Context;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import java.lang.reflect.Method;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 阶段 2.6：黑名单从文件读取
 */
public class ProbeHook implements IXposedHookLoadPackage {

    private static final String TAG = "NightLightProbe";
    private static final String PAPER_MODE_KEY = "screen_paper_mode_enabled";
    private static final String SAVED_KEY = "night_light_perapp_saved";
    private static final int SENTINEL_NONE = -1;
    private static final File BLACKLIST_FILE = new File("/data/system/nightlightperapp_blacklist.txt");

    private volatile Set<String> mBlacklist = new HashSet<>();

    // 反射缓存
    private Method sGetIntForUser;
    private Method sPutIntForUser;
    private int mUserCurrent = -2; // UserHandle.USER_CURRENT

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] 已加载 v2.6");

        // 从文件加载黑名单
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
                                int saved = settingsGetInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved == SENTINEL_NONE) {
                                    int current = settingsGetInt(cr, PAPER_MODE_KEY, 0);
                                    settingsPutInt(cr, SAVED_KEY, current);
                                    settingsPutInt(cr, PAPER_MODE_KEY, 0);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 保存原值=" + current + "，关闭护眼");
                                }
                            } else {
                                int saved = settingsGetInt(cr, SAVED_KEY, SENTINEL_NONE);
                                if (saved != SENTINEL_NONE) {
                                    settingsPutInt(cr, PAPER_MODE_KEY, saved);
                                    settingsPutInt(cr, SAVED_KEY, SENTINEL_NONE);
                                    XposedBridge.log("[" + TAG + "] " + pkg + " 到前台 → 恢复护眼=" + saved);
                                }
                            }

                            // 每次刷新黑名单（文件小，开销可忽略）
                            refreshBlacklist();

                        } catch (Throwable t) {
                            XposedBridge.log("[" + TAG + "] error: " + Log.getStackTraceString(t));
                        }
                    }
                }
        );

        XposedBridge.log("[" + TAG + "] Hook 完成");
    }

    private int settingsGetInt(ContentResolver cr, String key, int def) {
        try {
            if (sGetIntForUser == null) {
                sGetIntForUser = Settings.System.class.getMethod(
                        "getIntForUser", ContentResolver.class, String.class, int.class, int.class);
            }
            return (int) sGetIntForUser.invoke(null, cr, key, def, mUserCurrent);
        } catch (Throwable t) {
            // 降级到普通 API
            return Settings.System.getInt(cr, key, def);
        }
    }

    private void settingsPutInt(ContentResolver cr, String key, int value) {
        try {
            if (sPutIntForUser == null) {
                sPutIntForUser = Settings.System.class.getMethod(
                        "putIntForUser", ContentResolver.class, String.class, int.class, int.class);
            }
            sPutIntForUser.invoke(null, cr, key, value, mUserCurrent);
        } catch (Throwable t) {
            // 降级到普通 API
            Settings.System.putInt(cr, key, value);
        }
    }

    private void refreshBlacklist() {
        Set<String> newBlacklist = new HashSet<>();
        try {
            if (!BLACKLIST_FILE.exists()) return;
            BufferedReader reader = new BufferedReader(new FileReader(BLACKLIST_FILE));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    newBlacklist.add(line);
                }
            }
            reader.close();
            mBlacklist = newBlacklist;
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
