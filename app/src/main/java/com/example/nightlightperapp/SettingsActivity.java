package com.example.nightlightperapp;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * 阶段 3: 设置页 - 管理黑名单
 */
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
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

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

        mAdapter = new AppListAdapter();
        mListView.setAdapter(mAdapter);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mAdapter.filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        mSelectAll.setOnClickListener(v -> {
            for (AppItem item : mAllApps) {
                mBlacklist.add(item.packageName);
            }
            mAdapter.notifyDataSetChanged();
            updateStatus();
        });

        mDeselectAll.setOnClickListener(v -> {
            mBlacklist.clear();
            mAdapter.notifyDataSetChanged();
            updateStatus();
        });

        loadAppsAsync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveBlacklist();
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

            mMainHandler.post(() -> {
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
                mMainHandler.post(() ->
                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private Set<String> readBlacklist() {
        Set<String> blacklist = new HashSet<>();
        try {
            if (!BLACKLIST_FILE.exists()) return blacklist;
            BufferedReader reader = new BufferedReader(new FileReader(BLACKLIST_FILE));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    blacklist.add(line);
                }
            }
            reader.close();
        } catch (Exception e) {
            // 忽略
        }
        return blacklist;
    }

    private void updateStatus() {
        mStatusText.setText("已选择 " + mBlacklist.size() + " 个应用");
    }

    private class AppListAdapter extends BaseAdapter {
        private List<AppItem> mFiltered = new ArrayList<>();

        void filter(String query) {
            mFiltered.clear();
            query = query.toLowerCase();
            for (AppItem item : mAllApps) {
                if (item.label.toLowerCase().contains(query) ||
                    item.packageName.toLowerCase().contains(query)) {
                    mFiltered.add(item);
                }
            }
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
            h.checkbox.setChecked(mBlacklist.contains(item.packageName));

            convertView.setOnClickListener(v -> {
                if (mBlacklist.contains(item.packageName)) {
                    mBlacklist.remove(item.packageName);
                } else {
                    mBlacklist.add(item.packageName);
                }
                h.checkbox.setChecked(mBlacklist.contains(item.packageName));
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
