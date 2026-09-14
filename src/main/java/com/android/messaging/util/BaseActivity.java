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

import android.support.v7.app.AppCompatActivity;

import com.android.messaging.R;

/**
 * Common activity behavior for SimpleSMS.
 *
 * Android 15/16 enforce edge-to-edge for modern targets. The legacy AOSP/Support-28
 * action-bar layout predates that behavior, so on API 35+ we explicitly inset the
 * entire AppCompat sub-decor inside the status/navigation bars. Nougat through
 * Android 14 keep the platform's original layout behavior.
 */
public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context base) {
        int scale = AppPrefs.scale(base);
        if (scale == 100) {
            super.attachBaseContext(base);
            return;
        }
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.densityDpi = Math.max(120, Math.round(c.densityDpi * (scale / 100f)));
        super.attachBaseContext(base.createConfigurationContext(c));
    }

    @Override
    protected void onCreate(Bundle b) {
        setTheme(AppPrefs.isDark(this) ? R.style.BugleTheme_Dark : R.style.BugleTheme);
        super.onCreate(b);
        if (!AppPrefs.screenshots(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        installModernSystemBarInsets();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        installModernSystemBarInsets();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        installModernSystemBarInsets();
    }

    private void installModernSystemBarInsets() {
        if (Build.VERSION.SDK_INT < 35) return;

        final View decor = getWindow().getDecorView();
        final View content = decor.findViewById(android.R.id.content);
        if (content == null) return;

        View insetRoot = content;
        while (insetRoot.getParent() instanceof View && insetRoot.getParent() != decor) {
            insetRoot = (View) insetRoot.getParent();
        }

        final View target = insetRoot;
        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();

        target.setOnApplyWindowInsetsListener((v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + bars.bottom);
            return windowInsets;
        });
        target.requestApplyInsets();
    }
}
