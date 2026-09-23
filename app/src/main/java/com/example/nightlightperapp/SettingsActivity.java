package com.example.nightlightperapp;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 探针版设置页 - 只显示状态信息
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textView = new TextView(this);
        textView.setPadding(48, 48, 48, 48);
        textView.setTextSize(16);
        textView.setText("NightLightPerApp 探针版\n\n"
                + "状态: 已安装\n\n"
                + "请在 LSPosed 中启用此模块\n"
                + "作用域选择 \"系统框架\"\n"
                + "然后重启手机\n\n"
                + "重启后切换几个 App，\n"
                + "查看 LSPosed 日志中的输出");

        setContentView(textView);
    }
}
