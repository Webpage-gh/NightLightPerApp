package com.example.nightlightperapp;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private List<AppItem> mAllApps = new ArrayList<>();
    private Set<String> mBlacklist = new HashSet<>();
    private boolean mLoadingDone = false;

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

        // 搜索过滤
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

        // 全选/全不选
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

        // 异步加载应用列表
        new LoadAppsTask().execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mLoadingDone) {
            saveBlacklist();
        }
    }

    private void saveBlacklist() {
        StringBuilder sb = new StringBuilder();
        for (String pkg : mBlacklist) {
            sb.append(pkg).append("\n");
        }
        String content = sb.toString();

        // 用 su system -c 写入，确保权限正确
        try {
            String cmd = "su system -c \"echo '" + content.trim() + "' > " + BLACKLIST_FILE.getAbsolutePath() + "\"";
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd}).waitFor();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus() {
        mStatusText.setText("已选择 " + mBlacklist.size() + " 个应用");
    }

    /**
     * 异步加载已安装应用列表
     */
    private class LoadAppsTask extends AsyncTask<Void, AppItem, Void> {

        @Override
        protected void onPreExecute() {
            mLoading.setVisibility(View.VISIBLE);
            mListView.setVisibility(View.GONE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // 读取当前黑名单
            mBlacklist = readBlacklist();

            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo app : apps) {
                // 只显示用户应用（过滤系统核心应用）
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    String label = app.loadLabel(pm).toString();
                    Drawable icon = app.loadIcon(pm);
                    publishProgress(new AppItem(app.packageName, label, icon));
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(AppItem... values) {
            mAllApps.add(values[0]);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mLoading.setVisibility(View.GONE);
            mListView.setVisibility(View.VISIBLE);
            mLoadingDone = true;
            updateStatus();
        }
    }

    /**
     * 读取黑名单文件
     */
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

    /**
     * 应用列表适配器
     */
    private class AppListAdapter extends BaseAdapter {

        private List<AppItem> mFilteredApps = new ArrayList<>();

        public void filter(String query) {
            mFilteredApps.clear();
            query = query.toLowerCase();
            for (AppItem item : mAllApps) {
                if (item.label.toLowerCase().contains(query) ||
                    item.packageName.toLowerCase().contains(query)) {
                    mFilteredApps.add(item);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mFilteredApps.size();
        }

        @Override
        public Object getItem(int position) {
            return mFilteredApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(SettingsActivity.this)
                        .inflate(R.layout.list_item_app, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.app_icon);
                holder.name = convertView.findViewById(R.id.app_name);
                holder.pkg = convertView.findViewById(R.id.app_package);
                holder.checkbox = convertView.findViewById(R.id.app_checkbox);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppItem item = mFilteredApps.get(position);
            holder.icon.setImageDrawable(item.icon);
            holder.name.setText(item.label);
            holder.pkg.setText(item.packageName);
            holder.checkbox.setChecked(mBlacklist.contains(item.packageName));

            // 点击整行也能切换
            convertView.setOnClickListener(v -> {
                if (mBlacklist.contains(item.packageName)) {
                    mBlacklist.remove(item.packageName);
                } else {
                    mBlacklist.add(item.packageName);
                }
                holder.checkbox.setChecked(mBlacklist.contains(item.packageName));
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

    /**
     * 应用信息
     */
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
