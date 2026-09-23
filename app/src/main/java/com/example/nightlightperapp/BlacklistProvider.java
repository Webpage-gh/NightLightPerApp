package com.example.nightlightperapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * 黑名单 ContentProvider - 探针版空实现
 * 正式版会在这里存储黑名单数据
 */
public class BlacklistProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.nightlightperapp.blacklist";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/blacklist");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        // 探针版：返回空 Cursor
        return new MatrixCursor(new String[]{"package_name"});
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
