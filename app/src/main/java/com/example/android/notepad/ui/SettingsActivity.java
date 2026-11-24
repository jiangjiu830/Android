package com.example.android.notepad.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.widget.NestedScrollView;

import com.example.android.notepad.App;
import com.example.android.notepad.R;
import com.example.android.notepad.data.SortOrder;
import com.example.android.notepad.util.Prefs;
import com.example.android.notepad.util.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * 偏好设置：主题、默认排序、是否显示摘要、正文字号。
 * <p>
 * 所有设置保存在 SharedPreferences 中，列表页与编辑器在 onResume 时读取。
 */
public class SettingsActivity extends BaseActivity {

    private static final int FONT_MIN = 14;
    private static final int FONT_MAX = 26;
    private static final int FONT_STEP = 2;

    private ChipGroup mThemeGroup;
    private ChipGroup mSortGroup;
    private MaterialSwitch mPreviewSwitch;
    private Slider mFontSlider;
    private TextView mFontValue;

    /** 绑定设置项；所有修改即时写入 SharedPreferences，列表页返回时生效。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        mThemeGroup = findViewById(R.id.theme_group);
        mSortGroup = findViewById(R.id.sort_group);
        mPreviewSwitch = findViewById(R.id.switch_preview);
        mFontSlider = findViewById(R.id.slider_font);
        mFontValue = findViewById(R.id.text_font_value);
        TextView about = findViewById(R.id.text_about);

        setupToolbar(toolbar, true);

        setupTheme();
        setupSort();
        setupPreview();
        setupFontSize();

        about.setText(getString(R.string.settings_about_desc, versionName()));

        WindowInsetsHelper.padTop(findViewById(R.id.app_bar));
        WindowInsetsHelper.padBottomForIme((NestedScrollView) findViewById(R.id.scroll));
        applySystemBarAppearance();
    }

    /** 主题切换：写入偏好后由 AppCompatDelegate 触发界面重建。 */
    private void setupTheme() {
        CharSequence[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        Chips.populate(mThemeGroup, labels, clampTheme(Prefs.themeMode(this)), index -> {
            Prefs.setThemeMode(this, index);
            // 切换夜间模式会让 Activity 自动重建，重建后读取新的主题
            AppCompatDelegate.setDefaultNightMode(App.nightModeFor(index));
        });
    }

    /** 默认排序：只保存偏好，列表页在 onResume 时读取。 */
    private void setupSort() {
        final SortOrder[] orders = SortOrder.values();
        CharSequence[] labels = new CharSequence[orders.length];
        for (int i = 0; i < orders.length; i++) {
            labels[i] = getString(orders[i].labelRes());
        }
        Chips.populate(mSortGroup, labels, Prefs.sortOrder(this).ordinal(),
                index -> Prefs.setSortOrder(this, orders[index]));
    }

    /** 是否在列表中显示正文摘要。 */
    private void setupPreview() {
        mPreviewSwitch.setChecked(Prefs.showPreview(this));
        mPreviewSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> Prefs.setShowPreview(this, isChecked));
    }

    /** 正文字号：14–26sp，按 2sp 步进，实时写入偏好。 */
    private void setupFontSize() {
        final int size = clampFontSize(Prefs.fontSize(this));
        mFontSlider.setValueFrom(FONT_MIN);
        mFontSlider.setValueTo(FONT_MAX);
        mFontSlider.setStepSize(FONT_STEP);
        mFontSlider.setValue(size);
        mFontSlider.setLabelFormatter(value ->
                getString(R.string.settings_font_value, (int) value));
        mFontSlider.addOnChangeListener((slider, value, fromUser) -> {
            int newSize = (int) value;
            Prefs.setFontSize(this, newSize);
            updateFontValue(newSize);
        });
        updateFontValue(size);
    }

    /** 同步滑杆右侧的「16 sp」文本。 */
    private void updateFontValue(int size) {
        mFontValue.setText(getString(R.string.settings_font_value, size));
    }

    /** 容错：偏好里出现越界值时回退到「跟随系统」。 */
    private int clampTheme(int themeMode) {
        return themeMode < 0 || themeMode > Prefs.THEME_DARK ? Prefs.THEME_SYSTEM : themeMode;
    }

    /** 把字号限制在滑杆范围内，并对齐到步长。 */
    private int clampFontSize(int size) {
        if (size < FONT_MIN) {
            return FONT_MIN;
        }
        if (size > FONT_MAX) {
            return FONT_MAX;
        }
        // Slider 要求取值是 stepSize 的整数倍
        return size - (size - FONT_MIN) % FONT_STEP;
    }
}
