package com.example.android.notepad.data;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.example.android.notepad.R;

/**
 * 默认标签（原「分类」）的键、名称与颜色。
 * <p>
 * 首次建库时用这些定义初始化 tags 表；之后标签名称与颜色以数据库为准，
 * 用户可以自由新增、重命名、删除标签。
 */
public final class NoteCategory {

    public static final String DEFAULT = "default";
    public static final String WORK = "work";
    public static final String LIFE = "life";
    public static final String STUDY = "study";
    public static final String IDEA = "idea";

    /** 全部分类，顺序与编辑器的分类选择条一致 */
    public static final String[] KEYS = {DEFAULT, WORK, LIFE, STUDY, IDEA};

    private NoteCategory() {
    }

    /** 容错：未知分类一律归入「默认」 */
    public static String normalize(String key) {
        for (String k : KEYS) {
            if (k.equals(key)) {
                return key;
            }
        }
        return DEFAULT;
    }

    /** 是否是内置的默认标签 key */
    public static boolean isKnown(String key) {
        for (String k : KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** 分类在 {@link #KEYS} 中的下标，用于选中对应的 Chip */
    public static int indexOf(String key) {
        String normalized = normalize(key);
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    @StringRes
    public static int labelRes(String key) {
        switch (normalize(key)) {
            case WORK:
                return R.string.category_work;
            case LIFE:
                return R.string.category_life;
            case STUDY:
                return R.string.category_study;
            case IDEA:
                return R.string.category_idea;
            case DEFAULT:
            default:
                return R.string.category_default;
        }
    }

    @ColorRes
    public static int colorRes(String key) {
        switch (normalize(key)) {
            case WORK:
                return R.color.category_work;
            case LIFE:
                return R.color.category_life;
            case STUDY:
                return R.color.category_study;
            case IDEA:
                return R.color.category_idea;
            case DEFAULT:
            default:
                return R.color.category_default;
        }
    }
}
