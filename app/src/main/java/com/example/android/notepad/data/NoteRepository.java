package com.example.android.notepad.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.example.android.notepad.NotePad;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 笔记数据仓库：把 ContentResolver 的调用封装成面向对象的接口。
 * <p>
 * 界面层只与「笔记对象」打交道，SQL 查询条件（搜索、标签筛选、排序）都集中在这里构造。
 * 所有方法都是阻塞的，必须在后台线程调用（见 {@code util.Async}）。
 */
public final class NoteRepository {

    private static volatile NoteRepository sInstance;

    private final Context mContext;
    private final ContentResolver mResolver;

    private NoteRepository(Context context) {
        mContext = context.getApplicationContext();
        mResolver = mContext.getContentResolver();
    }

    public static NoteRepository get(Context context) {
        NoteRepository instance = sInstance;
        if (instance == null) {
            synchronized (NoteRepository.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new NoteRepository(context);
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    /**
     * 查询笔记。
     *
     * @param trash    是否查询回收站
     * @param keyword  搜索关键字（匹配标题或正文，null / 空串表示不过滤）
     * @param tagKey   标签 key（null 表示全部标签）
     * @param sort     排序方式
     */
    public List<Note> query(boolean trash, String keyword, String tagKey, SortOrder sort) {
        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<>();

        selection.append(NotePad.Notes.COLUMN_NAME_DELETED).append(trash ? "=1" : "=0");

        if (!TextUtils.isEmpty(tagKey)) {
            selection.append(" AND ").append(NotePad.Notes.COLUMN_NAME_CATEGORY).append("=?");
            args.add(tagKey);
        }

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (!trimmedKeyword.isEmpty()) {
            String like = "%" + escapeLike(trimmedKeyword) + "%";
            selection.append(" AND (")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? ESCAPE '\\'")
                    .append(" OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ? ESCAPE '\\'")
                    .append(")");
            args.add(like);
            args.add(like);
        }

        String orderBy = sort == null ? NotePad.Notes.DEFAULT_SORT_ORDER : sort.sqlOrder();
        Cursor cursor = mResolver.query(NotePad.Notes.CONTENT_URI, null,
                selection.toString(), args.toArray(new String[0]), orderBy);

        List<Note> notes = new ArrayList<>();
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    notes.add(Note.fromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }

        // 按标题排序时使用中文 Collator，得到符合拼音习惯的顺序
        if (sort == SortOrder.TITLE) {
            sortByTitle(notes);
        }
        applyTagInfo(notes);
        return notes;
    }

    /** 查询回收站中的笔记 */
    public List<Note> queryTrash() {
        return query(true, null, null, SortOrder.MODIFIED);
    }

    /** 查询全部笔记（含回收站），用于备份 */
    public List<Note> queryAll() {
        Cursor cursor = mResolver.query(NotePad.Notes.CONTENT_URI, null, null, null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        List<Note> notes = new ArrayList<>();
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    notes.add(Note.fromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return notes;
    }

    /** 按 id 查询单条笔记，不存在时返回 null */
    public Note find(long id) {
        Cursor cursor = mResolver.query(uriFor(id), null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    Note note = Note.fromCursor(cursor);
                    applyTagInfo(Collections.singletonList(note));
                    return note;
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /** 新增笔记，返回新记录的 id（失败返回 -1） */
    public long insert(Note note) {
        if (note.created <= 0) {
            note.created = System.currentTimeMillis();
        }
        if (note.modified <= 0) {
            note.modified = note.created;
        }
        Uri uri = mResolver.insert(NotePad.Notes.CONTENT_URI, note.toContentValues());
        return uri == null ? -1L : ContentUris.parseId(uri);
    }

    /** 更新笔记（同时刷新修改时间），返回受影响行数 */
    public int update(Note note) {
        note.modified = System.currentTimeMillis();
        return mResolver.update(uriFor(note.id), note.toContentValues(), null, null);
    }

    /** 置顶 / 取消置顶 */
    public int setPinned(long id, boolean pinned) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_PINNED, pinned ? 1 : 0);
        return mResolver.update(uriFor(id), values, null, null);
    }

    /** 移入 / 移出回收站（软删除） */
    public int setDeleted(long id, boolean deleted) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_DELETED, deleted ? 1 : 0);
        values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, deleted ? System.currentTimeMillis() : 0L);
        return mResolver.update(uriFor(id), values, null, null);
    }

    /** 彻底删除一条笔记 */
    public int deletePermanently(long id) {
        return mResolver.delete(uriFor(id), null, null);
    }

    /** 清空回收站 */
    public int emptyTrash() {
        return mResolver.delete(NotePad.Notes.CONTENT_URI,
                NotePad.Notes.COLUMN_NAME_DELETED + "=1", null);
    }

    /** 批量导入（从备份文件恢复），返回成功条数 */
    public int importNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        ContentValues[] values = new ContentValues[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            note.id = -1L;
            if (note.created <= 0) {
                note.created = now;
            }
            if (note.modified <= 0) {
                note.modified = note.created;
            }
            values[i] = note.toContentValues();
        }
        // 交给 Provider 的 bulkInsert，内部事务保证整批导入的原子性
        return mResolver.bulkInsert(NotePad.Notes.CONTENT_URI, values);
    }

    /* ===================== 标签 ===================== */

    /** 按创建顺序查询全部标签。 */
    public List<Tag> tags() {
        Cursor cursor = mResolver.query(NotePad.Tags.CONTENT_URI, null, null, null,
                NotePad.Tags.DEFAULT_SORT_ORDER);
        List<Tag> tags = new ArrayList<>();
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    tags.add(Tag.fromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return tags;
    }

    /** 按 key 查询标签，不存在时返回 null。 */
    public Tag findTag(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }
        Cursor cursor = mResolver.query(NotePad.Tags.CONTENT_URI, null,
                NotePad.Tags.COLUMN_TAG_KEY + "=?", new String[]{key}, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return Tag.fromCursor(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /** 新增标签；名称重复或为空时返回 null。 */
    public Tag addTag(String label) {
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty() || tagLabelExists(trimmed, null)) {
            return null;
        }
        Tag tag = new Tag();
        tag.key = generateTagKey();
        tag.label = trimmed;
        tag.color = TagPalette.colorForIndex(mContext, tags().size());
        tag.created = System.currentTimeMillis();
        Uri uri = mResolver.insert(NotePad.Tags.CONTENT_URI, tag.toContentValues());
        return uri == null ? null : tag;
    }

    /** 重命名标签；只改标签表，笔记通过 key 引用，无需批量更新。 */
    public int renameTag(String key, String label) {
        String trimmed = label == null ? "" : label.trim();
        if (TextUtils.isEmpty(key) || trimmed.isEmpty() || tagLabelExists(trimmed, key)) {
            return 0;
        }
        ContentValues values = new ContentValues();
        values.put(NotePad.Tags.COLUMN_LABEL, trimmed);
        return mResolver.update(NotePad.Tags.CONTENT_URI, values,
                NotePad.Tags.COLUMN_TAG_KEY + "=?", new String[]{key});
    }

    /**
     * 删除标签，并把使用它的笔记移到默认标签。
     * 默认标签不允许删除，返回 -1 作为提示。
     */
    public int deleteTag(String key) {
        if (TextUtils.isEmpty(key) || NoteCategory.DEFAULT.equals(key)) {
            return -1;
        }
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, NoteCategory.DEFAULT);
        mResolver.update(NotePad.Notes.CONTENT_URI, values,
                NotePad.Notes.COLUMN_NAME_CATEGORY + "=?", new String[]{key});
        return mResolver.delete(NotePad.Tags.CONTENT_URI,
                NotePad.Tags.COLUMN_TAG_KEY + "=?", new String[]{key});
    }

    /** 统计使用某个标签的笔记数（包含回收站，删除标签时要一起迁移）。 */
    public int countNotesWithTag(String key) {
        Cursor cursor = mResolver.query(NotePad.Notes.CONTENT_URI,
                new String[]{NotePad.Notes._ID},
                NotePad.Notes.COLUMN_NAME_CATEGORY + "=?", new String[]{key}, null);
        if (cursor == null) {
            return 0;
        }
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    /** 标签名是否已存在（排除正在重命名的那个 key）。 */
    public boolean tagLabelExists(String label, String excludeKey) {
        String target = label == null ? "" : label.trim();
        for (Tag tag : tags()) {
            if (excludeKey != null && excludeKey.equals(tag.key)) {
                continue;
            }
            if (tag.label.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /** 恢复备份：先确保标签存在，再批量导入笔记。 */
    public int importBackup(List<Tag> tags, List<Note> notes) {
        importTags(tags);
        ensureTagsForNotes(notes);
        return importNotes(notes);
    }

    private void importTags(List<Tag> tags) {
        if (tags == null) {
            return;
        }
        for (Tag tag : tags) {
            if (tag == null || TextUtils.isEmpty(tag.key) || TextUtils.isEmpty(tag.label)) {
                continue;
            }
            ContentValues values = tag.toContentValues();
            int rows = mResolver.update(NotePad.Tags.CONTENT_URI, values,
                    NotePad.Tags.COLUMN_TAG_KEY + "=?", new String[]{tag.key});
            if (rows == 0) {
                mResolver.insert(NotePad.Tags.CONTENT_URI, values);
            }
        }
    }

    /** 兼容旧备份：笔记引用了备份里没有的标签时，补一个标签定义。 */
    private void ensureTagsForNotes(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Map<String, Tag> existing = tagsAsMap();
        for (Note note : notes) {
            if (TextUtils.isEmpty(note.category)) {
                note.category = NoteCategory.DEFAULT;
            }
            if (existing.containsKey(note.category)) {
                continue;
            }
            Tag tag = new Tag();
            tag.key = note.category;
            tag.label = NoteCategory.isKnown(note.category)
                    ? mContext.getString(NoteCategory.labelRes(note.category))
                    : note.category;
            tag.color = NoteCategory.isKnown(note.category)
                    ? androidx.core.content.ContextCompat.getColor(
                    mContext, NoteCategory.colorRes(note.category))
                    : TagPalette.colorForIndex(mContext, existing.size());
            tag.created = System.currentTimeMillis();
            mResolver.insert(NotePad.Tags.CONTENT_URI, tag.toContentValues());
            existing.put(tag.key, tag);
        }
    }

    /** 用一次 tags 查询给笔记列表补上标签名称与颜色。 */
    private void applyTagInfo(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Map<String, Tag> tags = tagsAsMap();
        int fallbackColor = TagPalette.defaultColor(mContext);
        for (Note note : notes) {
            Tag tag = tags.get(note.category);
            if (tag != null) {
                note.tagLabel = tag.label;
                note.tagColor = tag.color;
            } else {
                note.tagLabel = note.category;
                note.tagColor = fallbackColor;
            }
        }
    }

    private Map<String, Tag> tagsAsMap() {
        Map<String, Tag> map = new LinkedHashMap<>();
        for (Tag tag : tags()) {
            map.put(tag.key, tag);
        }
        return map;
    }

    /** 生成不会重复的自定义标签 key（形如 tag_1720000000000）。 */
    private String generateTagKey() {
        String base = "tag_" + System.currentTimeMillis();
        String key = base;
        int suffix = 1;
        while (findTag(key) != null) {
            key = base + "_" + suffix++;
        }
        return key;
    }

    /** 拼出 content://.../notes/# 的单条笔记 URI。 */
    private Uri uriFor(long id) {
        return ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, id);
    }

    /** 标题排序：Collator 按中文拼音比较，置顶项始终优先。 */
    private void sortByTitle(List<Note> notes) {
        final Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(notes, (left, right) -> {
            if (left.pinned != right.pinned) {
                return left.pinned ? -1 : 1;
            }
            int result = collator.compare(left.displayTitle(mContext), right.displayTitle(mContext));
            return result != 0 ? result : Long.compare(right.modified, left.modified);
        });
    }

    /** 转义 LIKE 通配符，避免用户输入的 % 和 _ 被当成通配符 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
