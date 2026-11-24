package com.example.android.notepad.ui;

import android.view.LayoutInflater;
import android.view.View;

import com.example.android.notepad.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * ChipGroup 的辅助工具：用「筛选 Chip」快速构建一个单选选项条。
 * <p>
 * 用于笔记分类筛选（全部 / 工作 / 生活 …）以及设置页的主题、排序选择。
 */
public final class Chips {

    /** 选中项变化的回调，参数为选项下标 */
    public interface Listener {
        void onSelected(int index);
    }

    private Chips() {
    }

    /**
     * 填充选项。
     *
     * @param checkedIndex 默认选中项，-1 表示不选中
     * @param listener     选中变化回调（注册在初始选中之后，因此不会因初始化而触发）
     */
    public static void populate(ChipGroup group, CharSequence[] labels, int checkedIndex,
                                Listener listener) {
        group.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(group.getContext());
        for (int i = 0; i < labels.length; i++) {
            Chip chip = (Chip) inflater.inflate(R.layout.chip_filter, group, false);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            chip.setTag(i);
            group.addView(chip);
        }

        if (checkedIndex >= 0 && checkedIndex < group.getChildCount()) {
            ((Chip) group.getChildAt(checkedIndex)).setChecked(true);
        }

        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            View checked = chipGroup.findViewById(checkedIds.get(0));
            if (checked == null) {
                return;
            }
            int index = chipGroup.indexOfChild(checked);
            if (index >= 0 && listener != null) {
                listener.onSelected(index);
            }
        });
    }

    /** 以编程方式选中某一项（注意：会触发 Listener，调用方需自行加保护标记） */
    public static void select(ChipGroup group, int index) {
        if (index < 0 || index >= group.getChildCount()) {
            return;
        }
        ((Chip) group.getChildAt(index)).setChecked(true);
    }
}
