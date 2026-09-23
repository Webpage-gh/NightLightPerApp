package com.example.nightlightperapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 黑名单 ContentProvider
 * 通过 call() 提供黑名单数据
 */
public class BlacklistProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.nightlightperapp.blacklist";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/blacklist");
    public static final String METHOD_GET_BLACKLIST = "getBlacklist";
    public static final String METHOD_SET_BLACKLIST = "setBlacklist";
    public static final String KEY_PACKAGES = "packages";

    // 内存存储（简单实现）
    private static final java.util.Set<String> sBlacklist = new java.util.HashSet<>();

    @Override
    public boolean onCreate() {
        // 默认加入相册用于测试
        sBlacklist.add("com.miui.gallery");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();

        switch (method) {
            case METHOD_GET_BLACKLIST:
                result.putStringArrayList(KEY_PACKAGES, new java.util.ArrayList<>(sBlacklist));
                break;

            case METHOD_SET_BLACKLIST:
                if (extras != null) {
                    java.util.ArrayList<String> list = extras.getStringArrayList(KEY_PACKAGES);
                    sBlacklist.clear();
                    if (list != null) {
                        sBlacklist.addAll(list);
                    }
                }
                result.putBoolean("success", true);
                break;

            default:
                result.putString("error", "unknown method: " + method);
                break;
        }

        return result;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".blacklist";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
