package com.example.android.notepad.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import com.example.android.notepad.NotePad;
import com.example.android.notepad.R;

/**
 * 一条笔记的数据模型：把 Cursor 的一行转换成对象，便于在 RecyclerView 中使用。
 */
public class Note {

    /** 数据库主键，-1 表示尚未保存的新笔记 */
    public long id = -1L;
    public String title = "";
    public String content = "";
    public long created;
    public long modified;
    /** 标签 key（数据库列名沿用 category，兼容旧版本） */
    public String category = NoteCategory.DEFAULT;
    /** 标签显示名与颜色：由 Repository 查询 tags 表后填充，不属于 notes 表 */
    public String tagLabel = "";
    public int tagColor;
    public boolean pinned;
    public boolean deleted;
    public long deletedAt;

    /** 从查询结果构造对象 */
    public static Note fromCursor(Cursor cursor) {
        Note note = new Note();
        note.id = cursor.getLong(cursor.getColumnIndexOrThrow(NotePad.Notes._ID));
        note.title = safeString(cursor, NotePad.Notes.COLUMN_NAME_TITLE);
        note.content = safeString(cursor, NotePad.Notes.COLUMN_NAME_NOTE);
        note.created = cursor.getLong(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_CREATE_DATE));
        note.modified = cursor.getLong(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
        note.category = safeString(cursor, NotePad.Notes.COLUMN_NAME_CATEGORY);
        if (TextUtils.isEmpty(note.category)) {
            note.category = NoteCategory.DEFAULT;
        }
        note.pinned = cursor.getInt(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_PINNED)) != 0;
        note.deleted = cursor.getInt(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_DELETED)) != 0;
        note.deletedAt = cursor.getLong(
                cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_DELETED_AT));
        return note;
    }

    /** 转换成写入数据库的 ContentValues */
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_TITLE, title == null ? "" : title);
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, content == null ? "" : content);
        values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, created);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, modified);
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                TextUtils.isEmpty(category) ? NoteCategory.DEFAULT : category);
        values.put(NotePad.Notes.COLUMN_NAME_PINNED, pinned ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_DELETED, deleted ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, deletedAt);
        return values;
    }

    /** 标题和正文都为空 */
    public boolean isEmpty() {
        return TextUtils.isEmpty(title == null ? "" : title.trim())
                && TextUtils.isEmpty(content == null ? "" : content.trim());
    }

    /**
     * 列表中展示的标题：优先用用户填写的标题；
     * 没有标题时退化为正文的第一行，让列表更易读。
     */
    public String displayTitle(Context context) {
        String trimmedTitle = title == null ? "" : title.trim();
        if (!trimmedTitle.isEmpty()) {
            return trimmedTitle;
        }
        String firstLine = firstContentLine();
        if (!firstLine.isEmpty()) {
            return firstLine;
        }
        return context.getString(R.string.note_untitled);
    }

    private String firstContentLine() {
        if (TextUtils.isEmpty(content)) {
            return "";
        }
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 28 ? trimmed.substring(0, 28) + "…" : trimmed;
            }
        }
        return "";
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
