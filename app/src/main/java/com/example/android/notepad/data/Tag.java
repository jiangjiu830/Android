package com.example.android.notepad.data;

import android.content.ContentValues;
import android.database.Cursor;

import com.example.android.notepad.NotePad;

/**
 * 用户标签。一条笔记对应一个标签，标签本身可由用户新增、重命名、删除。
 * <p>
 * {@code key} 是稳定主键，笔记的 {@code category} 列保存它；
 * {@code label} 是用户看到的名称，改名不会影响已有笔记。
 */
public class Tag {

    public String key = "";
    public String label = "";
    public int color;
    public long created;

    /** 从 tags 表的一行构造标签 */
    public static Tag fromCursor(Cursor cursor) {
        Tag tag = new Tag();
        tag.key = safeString(cursor, NotePad.Tags.COLUMN_TAG_KEY);
        tag.label = safeString(cursor, NotePad.Tags.COLUMN_LABEL);
        tag.color = cursor.getInt(cursor.getColumnIndexOrThrow(NotePad.Tags.COLUMN_COLOR));
        tag.created = cursor.getLong(cursor.getColumnIndexOrThrow(NotePad.Tags.COLUMN_CREATED));
        return tag;
    }

    /** 转成写入 tags 表的 ContentValues */
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Tags.COLUMN_TAG_KEY, key);
        values.put(NotePad.Tags.COLUMN_LABEL, label);
        values.put(NotePad.Tags.COLUMN_COLOR, color);
        values.put(NotePad.Tags.COLUMN_CREATED, created);
        return values;
    }

    private static String safeString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) {
            return "";
        }
        String value = cursor.getString(index);
        return value == null ? "" : value;
    }
}
