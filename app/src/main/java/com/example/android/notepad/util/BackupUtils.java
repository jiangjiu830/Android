package com.example.android.notepad.util;

import android.content.Context;
import android.text.TextUtils;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.data.NoteCategory;
import com.example.android.notepad.data.Tag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔记备份 / 恢复工具：把笔记与标签写成 JSON 文件，或从 JSON 文件读回。
 * <p>
 * 使用系统自带的 org.json，无需引入第三方库；
 * 文件读写通过 SAF（系统文件选择器）完成，因此不需要申请存储权限。
 * 旧版本备份没有 tags 字段，读取时会得到空标签列表，笔记仍可正常恢复。
 */
public final class BackupUtils {

    /** 备份文件的 MIME 类型 */
    public static final String MIME_TYPE = "application/json";

    private static final String KEY_APP = "app";
    private static final String KEY_VERSION = "version";
    private static final String KEY_EXPORTED_AT = "exportedAt";
    private static final String KEY_COUNT = "count";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_TAGS = "tags";

    private static final String KEY_TITLE = "title";
    private static final String KEY_NOTE = "note";
    private static final String KEY_CREATED = "created";
    private static final String KEY_MODIFIED = "modified";
    private static final String KEY_CATEGORY = "category";
    private static final String KEY_PINNED = "pinned";
    private static final String KEY_DELETED = "deleted";
    private static final String KEY_DELETED_AT = "deletedAt";

    private static final String KEY_TAG_KEY = "key";
    private static final String KEY_TAG_LABEL = "label";
    private static final String KEY_TAG_COLOR = "color";

    private static final String APP_TAG = "NotePad";
    private static final int FORMAT_VERSION = 2;
    private static final int FALLBACK_COLOR = 0xFF7A8794;

    private BackupUtils() {
    }

    /** 备份内容：标签库 + 笔记列表。 */
    public static final class Backup {
        public final List<Tag> tags;
        public final List<Note> notes;

        Backup(List<Tag> tags, List<Note> notes) {
            this.tags = tags;
            this.notes = notes;
        }
    }

    /** 备份文件的建议文件名，例如「记事本备份-20261115-1841.json」 */
    public static String suggestedFileName(Context context) {
        return context.getString(R.string.backup_file_name,
                TimeUtils.formatFileNameStamp(System.currentTimeMillis()));
    }

    /** 把标签与笔记写成 JSON 文本。 */
    public static void write(OutputStream outputStream, List<Note> notes, List<Tag> tags)
            throws IOException, JSONException {
        JSONArray tagsArray = new JSONArray();
        if (tags != null) {
            for (Tag tag : tags) {
                JSONObject item = new JSONObject();
                item.put(KEY_TAG_KEY, tag.key);
                item.put(KEY_TAG_LABEL, tag.label);
                item.put(KEY_TAG_COLOR, tag.color);
                item.put(KEY_CREATED, tag.created);
                tagsArray.put(item);
            }
        }

        JSONArray notesArray = new JSONArray();
        if (notes != null) {
            for (Note note : notes) {
                JSONObject item = new JSONObject();
                item.put(KEY_TITLE, note.title == null ? "" : note.title);
                item.put(KEY_NOTE, note.content == null ? "" : note.content);
                item.put(KEY_CREATED, note.created);
                item.put(KEY_MODIFIED, note.modified);
                item.put(KEY_CATEGORY, TextUtils.isEmpty(note.category)
                        ? NoteCategory.DEFAULT : note.category);
                item.put(KEY_PINNED, note.pinned ? 1 : 0);
                item.put(KEY_DELETED, note.deleted ? 1 : 0);
                item.put(KEY_DELETED_AT, note.deletedAt);
                notesArray.put(item);
            }
        }

        JSONObject root = new JSONObject();
        root.put(KEY_APP, APP_TAG);
        root.put(KEY_VERSION, FORMAT_VERSION);
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis());
        root.put(KEY_COUNT, notesArray.length());
        root.put(KEY_TAGS, tagsArray);
        root.put(KEY_NOTES, notesArray);

        outputStream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /** 读取备份文件，返回标签与笔记；格式不正确时抛出 JSONException。 */
    public static Backup read(InputStream inputStream) throws IOException, JSONException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8 * 1024];
        int length;
        while ((length = inputStream.read(chunk)) > 0) {
            buffer.write(chunk, 0, length);
        }

        String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        JSONObject root = new JSONObject(json);
        JSONArray notesArray = root.optJSONArray(KEY_NOTES);
        if (notesArray == null) {
            throw new JSONException("备份文件中没有 notes 字段");
        }

        long now = System.currentTimeMillis();
        List<Tag> tags = new ArrayList<>();
        JSONArray tagsArray = root.optJSONArray(KEY_TAGS);
        if (tagsArray != null) {
            for (int i = 0; i < tagsArray.length(); i++) {
                JSONObject item = tagsArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString(KEY_TAG_KEY, "");
                String label = item.optString(KEY_TAG_LABEL, "");
                if (TextUtils.isEmpty(key) || TextUtils.isEmpty(label)) {
                    continue;
                }
                Tag tag = new Tag();
                tag.key = key;
                tag.label = label;
                tag.color = item.optInt(KEY_TAG_COLOR, FALLBACK_COLOR);
                tag.created = item.optLong(KEY_CREATED, now + i);
                tags.add(tag);
            }
        }

        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < notesArray.length(); i++) {
            JSONObject item = notesArray.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Note note = new Note();
            note.title = item.optString(KEY_TITLE, "");
            note.content = item.optString(KEY_NOTE, "");
            note.created = item.optLong(KEY_CREATED, now);
            note.modified = item.optLong(KEY_MODIFIED, note.created);
            String category = item.optString(KEY_CATEGORY, NoteCategory.DEFAULT);
            note.category = TextUtils.isEmpty(category) ? NoteCategory.DEFAULT : category;
            note.pinned = item.optInt(KEY_PINNED, 0) != 0;
            note.deleted = item.optInt(KEY_DELETED, 0) != 0;
            note.deletedAt = item.optLong(KEY_DELETED_AT, 0L);
            notes.add(note);
        }
        return new Backup(tags, notes);
    }

    /** 分享用纯文本：有标题时「标题 + 空行 + 正文」。 */
    public static String toShareText(String title, String content) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            builder.append(title.trim()).append("\n\n");
        }
        builder.append(content == null ? "" : content);
        return builder.toString();
    }
}
