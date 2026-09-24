package com.example.nightlightperapp;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {

    private static final File BLACKLIST_FILE = new File("/data/system/nightlightperapp_blacklist.txt");

    private EditText mSearchBox;
    private ListView mListView;
    private ProgressBar mLoading;
    private Button mSelectAll;
    private Button mDeselectAll;
    private TextView mStatusText;

    private AppListAdapter mAdapter;
    private final List<AppItem> mAllApps = new ArrayList<>();
    private final Set<String> mBlacklist = new HashSet<>();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private boolean mModified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mSearchBox = findViewById(R.id.search_box);
        mListView = findViewById(R.id.app_list);
        mLoading = findViewById(R.id.loading);
        mSelectAll = findViewById(R.id.select_all);
        mDeselectAll = findViewById(R.id.deselect_all);
        mStatusText = findViewById(R.id.status_text);

        // Edge-to-edge
        getWindow().setDecorFitsSystemWindows(false);
        mListView.setClipToPadding(false);
        View root = findViewById(R.id.root);
        // 保存原始 padding
        final int basePadL = root.getPaddingLeft();
        final int basePadR = root.getPaddingRight();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left + basePadL, bars.top + 16, bars.right + basePadR, 0);
            mListView.setPadding(
                mListView.getPaddingLeft(),
                mListView.getPaddingTop(),
                mListView.getPaddingRight(),
                bars.bottom
            );
            return WindowInsets.CONSUMED;
        });

        mAdapter = new AppListAdapter();
        mListView.setAdapter(mAdapter);

        // 点击列表项时收起键盘
        mListView.setOnItemClickListener((parent, view, position, id) -> hideKeyboard());

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        mSelectAll.setText(R.string.select_all);
        mDeselectAll.setText(R.string.deselect_all);
        mSearchBox.setHint(R.string.search_hint);

        mSelectAll.setOnClickListener(v -> {
            for (AppItem item : mAdapter.mFiltered) {
                mBlacklist.add(item.packageName);
            }
            mModified = true;
            mAdapter.notifyDataSetChanged();
            updateStatus();
        });

        mDeselectAll.setOnClickListener(v -> {
            for (AppItem item : mAdapter.mFiltered) {
                mBlacklist.remove(item.packageName);
            }
            mModified = true;
            mAdapter.notifyDataSetChanged();
            updateStatus();
        });

        loadAppsAsync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
        if (mModified) {
            saveBlacklist();
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused instanceof EditText) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void loadAppsAsync() {
        mLoading.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            mBlacklist.addAll(readBlacklist());

            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            List<AppItem> result = new ArrayList<>();

            for (ApplicationInfo app : apps) {
                String label = app.loadLabel(pm).toString();
                Drawable icon = app.loadIcon(pm);
                result.add(new AppItem(app.packageName, label, icon));
            }

            // 已勾选排前面，再按名称排序
            Collections.sort(result, (a, b) -> {
                boolean aChecked = mBlacklist.contains(a.packageName);
                boolean bChecked = mBlacklist.contains(b.packageName);
                if (aChecked != bChecked) return aChecked ? -1 : 1;
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            });

            mMain.post(() -> {
                mAllApps.addAll(result);
                mAdapter.filter(mSearchBox.getText().toString());
                mLoading.setVisibility(View.GONE);
                mListView.setVisibility(View.VISIBLE);
                updateStatus();
            });
        });
    }

    private void saveBlacklist() {
        StringBuilder sb = new StringBuilder();
        for (String pkg : mBlacklist) {
            sb.append(pkg).append("\n");
        }
        String content = sb.toString().trim();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String escaped = content.replace("'", "'\\''");
                String cmd = "su system -c 'echo \"" + escaped + "\" > " + BLACKLIST_FILE.getAbsolutePath() + "'";
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                p.waitFor();
            } catch (Exception e) {
                mMain.post(() ->
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private Set<String> readBlacklist() {
        Set<String> blacklist = new HashSet<>();
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"su", "-c", "cat " + BLACKLIST_FILE.getAbsolutePath()});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    blacklist.add(line);
                }
            }
            reader.close();
            p.waitFor();
        } catch (Exception e) {
            // 忽略
        }
        return blacklist;
    }

    private void updateStatus() {
        mStatusText.setText(getString(R.string.status_format, mBlacklist.size()));
    }

    private class AppListAdapter extends BaseAdapter {
        List<AppItem> mFiltered = new ArrayList<>();

        void filter(String query) {
            mFiltered.clear();
            query = query.toLowerCase();
            for (AppItem item : mAllApps) {
                if (item.label.toLowerCase().contains(query) ||
                    item.packageName.toLowerCase().contains(query)) {
                    mFiltered.add(item);
                }
            }
            // 搜索结果也保持已勾选在前（保持相对顺序）
            Collections.sort(mFiltered, (a, b) -> {
                boolean aChecked = mBlacklist.contains(a.packageName);
                boolean bChecked = mBlacklist.contains(b.packageName);
                if (aChecked != bChecked) return aChecked ? -1 : 1;
                return 0; // 相同组内保持原序
            });
            notifyDataSetChanged();
        }

        @Override public int getCount() { return mFiltered.size(); }
        @Override public Object getItem(int pos) { return mFiltered.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            ViewHolder h;
            if (convertView == null) {
                convertView = LayoutInflater.from(SettingsActivity.this)
                        .inflate(R.layout.list_item_app, parent, false);
                h = new ViewHolder();
                h.icon = convertView.findViewById(R.id.app_icon);
                h.name = convertView.findViewById(R.id.app_name);
                h.pkg = convertView.findViewById(R.id.app_package);
                h.checkbox = convertView.findViewById(R.id.app_checkbox);
                convertView.setTag(h);
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            AppItem item = mFiltered.get(pos);
            h.icon.setImageDrawable(item.icon);
            h.name.setText(item.label);
            h.pkg.setText(item.packageName);

            // 防止复用时触发 listener
            h.checkbox.setOnCheckedChangeListener(null);
            h.checkbox.setChecked(mBlacklist.contains(item.packageName));
            h.checkbox.setOnCheckedChangeListener((btn, checked) -> {
                if (checked) {
                    mBlacklist.add(item.packageName);
                } else {
                    mBlacklist.remove(item.packageName);
                }
                mModified = true;
                updateStatus();
            });

            return convertView;
        }

        class ViewHolder {
            ImageView icon;
            TextView name;
            TextView pkg;
            CheckBox checkbox;
        }
    }

    static class AppItem {
        String packageName;
        String label;
        Drawable icon;

        AppItem(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }
}
