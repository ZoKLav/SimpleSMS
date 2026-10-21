package com.android.messaging.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.widget.Toolbar;
import com.android.messaging.R;

/** One measured column owns the toolbar, screen content, and system insets. */
public abstract class BaseActivity extends AppCompatActivity {
    private FrameLayout screen;

    @Override protected void attachBaseContext(Context base) {
        int scale = AppPrefs.scale(base);
        if (scale == 100) { super.attachBaseContext(base); return; }
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.densityDpi = Math.max(120, Math.round(c.densityDpi * (scale / 100f)));
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override protected void onCreate(Bundle state) {
        setTheme(AppPrefs.isDark(this) ? R.style.BugleTheme_Dark : R.style.BugleTheme);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(AppPrefs.isDark(this) ? 0xff181b20 : 0xfff7f8fa);
        Toolbar toolbar = new Toolbar(new ContextThemeWrapper(this, R.style.SimpleSMSToolbarTheme));
        toolbar.setPopupTheme(AppPrefs.isDark(this)
                ? R.style.SimpleSMSPopupThemeDark : R.style.SimpleSMSPopupThemeLight);
        toolbar.setBackgroundColor(AppPrefs.accent(this));
        toolbar.setTitleTextAppearance(this, R.style.BugleActionBarTitleTextStyle);
        toolbar.setContentInsetsRelative(UiUtil.dp(this, 20), UiUtil.dp(this, 12));
        toolbar.setMinimumHeight(UiUtil.dp(this, 64));
        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        screen = new FrameLayout(this);
        shell.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1));
        super.setContentView(shell);
        setSupportActionBar(toolbar);
        // On older Android the window already resizes for system bars and the IME.
        // On API 30+ consume insets once, outside BOTH toolbar and screen content.
        if (Build.VERSION.SDK_INT >= 30) {
            shell.setOnApplyWindowInsetsListener((v, insets) -> {
                android.view.WindowInsetsController controller = v.getWindowInsetsController();
                if (controller != null) {
                    int lightBars = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(
                            AppPrefs.isDark(this) ? 0 : lightBars, lightBars);
                }
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            });
            shell.requestApplyInsets();
        }
    }

    @Override protected void onResume(){
        super.onResume();
        android.app.KeyguardManager km=(android.app.KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(!com.android.messaging.security.Vault.unlocked() || (com.android.messaging.security.VaultKeys.lockEnabled(this)&&km!=null&&km.isDeviceLocked())){
            com.android.messaging.security.Vault.lock();
            startActivity(new android.content.Intent(this,com.android.messaging.security.UnlockActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }
    }
    @Override public void setContentView(int resource) {
        screen.removeAllViews();
        getLayoutInflater().inflate(resource, screen, true);
    }
    @Override public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(-1, -1));
    }
    @Override public void setContentView(View view, ViewGroup.LayoutParams params) {
        screen.removeAllViews();
        screen.addView(view, params);
    }
}
