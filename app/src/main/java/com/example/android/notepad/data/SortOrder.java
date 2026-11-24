package com.example.android.notepad.data;

import androidx.annotation.StringRes;

import com.example.android.notepad.R;

/**
 * 笔记排序方式。
 * <p>
 * 置顶的笔记在任何排序方式下都排在最前面，因此 SQL 排序条件都以 pinned DESC 开头；
 * 按标题排序需要中文拼音顺序，SQLite 的默认比较无法满足，改为在内存中用
 * {@link java.text.Collator} 排序（见 NoteRepository）。
 */
public enum SortOrder {

    MODIFIED("modified", "pinned DESC, modified DESC"),
    CREATED("created", "pinned DESC, created DESC"),
    TITLE("title", "pinned DESC, modified DESC");

    /** 持久化到 SharedPreferences 用的 key */
    public final String key;

    private final String sqlOrder;

    SortOrder(String key, String sqlOrder) {
        this.key = key;
        this.sqlOrder = sqlOrder;
    }

    /** 传给 ContentProvider 的 ORDER BY 子句 */
    public String sqlOrder() {
        return sqlOrder;
    }

    @StringRes
    public int labelRes() {
        switch (this) {
            case CREATED:
                return R.string.sort_created;
            case TITLE:
                return R.string.sort_title;
            case MODIFIED:
            default:
                return R.string.sort_modified;
        }
    }

    public static SortOrder fromKey(String key) {
        for (SortOrder order : values()) {
            if (order.key.equals(key)) {
                return order;
            }
        }
        return MODIFIED;
    }
}
