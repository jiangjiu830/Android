/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.example.android.notepad.data.NoteCategory;

/**
 * 笔记数据的 ContentProvider 实现：对外暴露 content:// 接口，内部基于 SQLite。
 * <p>
 * 与课程提供的 NotePad 源码相比，这里：
 * <ul>
 *     <li>补充了 category / pinned / deleted / deleted_at 字段（对应分类、置顶、回收站）；</li>
 *     <li>数据库升级时保留原有数据（逐列 ALTER TABLE），而不是直接删表重建；</li>
 *     <li>重写了 bulkInsert，用事务批量导入备份文件。</li>
 * </ul>
 */
public class NotePadProvider extends ContentProvider {

    private static final String TAG = "NotePadProvider";

    /** 数据库文件名 */
    private static final String DATABASE_NAME = "note_pad.db";

    /** 数据库版本：1 = 原版结构，2 = 增加分类，3 = 增加置顶 / 回收站，4 = 增加标签表 */
    private static final int DATABASE_VERSION = 4;

    private static final String TABLE = NotePad.Notes.TABLE_NAME;
    private static final String TAG_TABLE = NotePad.Tags.TABLE_NAME;

    /* UriMatcher 的匹配码 */
    private static final int NOTES = 1;
    private static final int NOTE_ID = 2;
    private static final int TAGS = 3;

    private static final UriMatcher sUriMatcher = buildUriMatcher();

    private DatabaseHelper mOpenHelper;

    /** 注册 notes（集合）、notes/#（单条）与 tags（集合）三种 URI。 */
    private static UriMatcher buildUriMatcher() {
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(NotePad.AUTHORITY, "notes", NOTES);
        matcher.addURI(NotePad.AUTHORITY, "notes/#", NOTE_ID);
        matcher.addURI(NotePad.AUTHORITY, "tags", TAGS);
        return matcher;
    }

    /** 只创建 Helper；数据库真正打开推迟到第一次访问。 */
    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    /** 查询：笔记集合/单条，或标签集合。 */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String defaultOrder;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                qb.setTables(TABLE);
                defaultOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                break;
            case NOTE_ID:
                qb.setTables(TABLE);
                qb.appendWhere(NotePad.Notes._ID + "="
                        + uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION));
                defaultOrder = NotePad.Notes.DEFAULT_SORT_ORDER;
                break;
            case TAGS:
                qb.setTables(TAG_TABLE);
                defaultOrder = NotePad.Tags.DEFAULT_SORT_ORDER;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String orderBy = TextUtils.isEmpty(sortOrder) ? defaultOrder : sortOrder;

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        Context context = getContext();
        if (context != null) {
            cursor.setNotificationUri(context.getContentResolver(), uri);
        }
        return cursor;
    }

    /** 返回笔记集合/单条或标签集合对应的 MIME 类型。 */
    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case NOTES:
                return NotePad.Notes.CONTENT_TYPE;
            case NOTE_ID:
                return NotePad.Notes.CONTENT_ITEM_TYPE;
            case TAGS:
                return NotePad.Tags.CONTENT_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    /** 插入一条笔记或一个标签，补齐默认值后返回对应集合 URI。 */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != TAGS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        if (match == TAGS) {
            ContentValues values = fillTagDefaults(initialValues);
            if (TextUtils.isEmpty(values.getAsString(NotePad.Tags.COLUMN_TAG_KEY))
                    || TextUtils.isEmpty(values.getAsString(NotePad.Tags.COLUMN_LABEL))) {
                throw new IllegalArgumentException("标签的 key 与 label 不能为空");
            }
            long rowId = db.insert(TAG_TABLE, null, values);
            if (rowId <= 0) {
                throw new SQLException("Failed to insert tag into " + uri);
            }
            notifyChange(uri);
            return NotePad.Tags.CONTENT_URI;
        }

        ContentValues values = fillDefaults(initialValues);
        long rowId = db.insert(TABLE, NotePad.Notes.COLUMN_NAME_NOTE, values);

        if (rowId <= 0) {
            throw new SQLException("Failed to insert row into " + uri);
        }

        Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);
        notifyChange(uri);
        return noteUri;
    }

    /** 批量插入：一个事务内完成，备份恢复不会出现「只导入一半」。 */
    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int match = sUriMatcher.match(uri);
        if (match != NOTES && match != TAGS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (values == null || values.length == 0) {
            return 0;
        }

        boolean isTag = match == TAGS;
        String table = isTag ? TAG_TABLE : TABLE;
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            for (ContentValues value : values) {
                ContentValues prepared = isTag ? fillTagDefaults(value) : fillDefaults(value);
                if (db.insert(table, null, prepared) > 0) {
                    count++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        notifyChange(uri);
        return count;
    }

    /** 删除：笔记单条 URI 会把 _ID 条件拼到 where 前面；标签按 where 删除。 */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.delete(TABLE, where, whereArgs);
                break;
            case TAGS:
                count = db.delete(TAG_TABLE, where, whereArgs);
                break;
            case NOTE_ID: {
                String id = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                String finalWhere = NotePad.Notes._ID + " = " + id;
                if (!TextUtils.isEmpty(where)) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.delete(TABLE, finalWhere, whereArgs);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        notifyChange(uri);
        return count;
    }

    /** 更新：笔记单条 URI 自动追加 _ID 条件；标签按 where 更新。 */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;

        switch (sUriMatcher.match(uri)) {
            case NOTES:
                count = db.update(TABLE, values, where, whereArgs);
                break;
            case TAGS:
                count = db.update(TAG_TABLE, values, where, whereArgs);
                break;
            case NOTE_ID: {
                String id = uri.getPathSegments().get(NotePad.Notes.NOTE_ID_PATH_POSITION);
                String finalWhere = NotePad.Notes._ID + " = " + id;
                if (!TextUtils.isEmpty(where)) {
                    finalWhere = finalWhere + " AND " + where;
                }
                count = db.update(TABLE, values, finalWhere, whereArgs);
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        notifyChange(uri);
        return count;
    }

    /** 通知观察者数据已经变化（列表页在 onResume 时也会主动刷新） */
    private void notifyChange(Uri uri) {
        Context context = getContext();
        if (context != null) {
            context.getContentResolver().notifyChange(uri, null);
            // 单条记录变化时，列表 URI 的观察者同样需要刷新
            if (sUriMatcher.match(uri) == NOTE_ID) {
                context.getContentResolver().notifyChange(NotePad.Notes.CONTENT_URI, null);
            }
        }
    }

    /** 补齐插入时缺失的默认值：时间戳、标题、正文、分类、置顶、删除标记 */
    private ContentValues fillDefaults(ContentValues initialValues) {
        ContentValues values = initialValues == null
                ? new ContentValues()
                : new ContentValues(initialValues);
        long now = System.currentTimeMillis();

        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_TITLE)) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, "");
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_NOTE)) {
            values.put(NotePad.Notes.COLUMN_NAME_NOTE, "");
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY)) {
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, NoteCategory.DEFAULT);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_PINNED)) {
            values.put(NotePad.Notes.COLUMN_NAME_PINNED, 0);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_DELETED)) {
            values.put(NotePad.Notes.COLUMN_NAME_DELETED, 0);
        }
        if (!values.containsKey(NotePad.Notes.COLUMN_NAME_DELETED_AT)) {
            values.put(NotePad.Notes.COLUMN_NAME_DELETED_AT, 0);
        }
        return values;
    }

    /** 补齐标签插入时的颜色与创建时间；key / label 由调用方保证非空。 */
    private ContentValues fillTagDefaults(ContentValues initialValues) {
        ContentValues values = initialValues == null
                ? new ContentValues()
                : new ContentValues(initialValues);

        if (!values.containsKey(NotePad.Tags.COLUMN_CREATED)) {
            values.put(NotePad.Tags.COLUMN_CREATED, System.currentTimeMillis());
        }
        if (!values.containsKey(NotePad.Tags.COLUMN_COLOR)) {
            Context context = getContext();
            int fallback = context == null ? 0xFF7A8794
                    : ContextCompat.getColor(context, R.color.category_default);
            values.put(NotePad.Tags.COLUMN_COLOR, fallback);
        }
        return values;
    }

    /**
     * 数据库帮助类：负责建表和升级。
     */
    static class DatabaseHelper extends SQLiteOpenHelper {

        private final Context mContext;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context.getApplicationContext();
        }

        /** 首次建表：notes + tags，并写入默认标签。 */
        @Override
        public void onCreate(SQLiteDatabase db) {
            createNotesTable(db);
            createTagsTable(db);
            seedDefaultTags(db);
        }

        /** 升级只加表/列、不删表，保证用户已有笔记不丢失。 */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.i(TAG, "升级数据库：" + oldVersion + " -> " + newVersion);
            // 保留用户已有笔记：只补充缺少的列
            if (oldVersion < 2) {
                addColumnIfMissing(db, NotePad.Notes.COLUMN_NAME_CATEGORY,
                        "TEXT DEFAULT '" + NoteCategory.DEFAULT + "'");
            }
            if (oldVersion < 3) {
                addColumnIfMissing(db, NotePad.Notes.COLUMN_NAME_PINNED, "INTEGER DEFAULT 0");
                addColumnIfMissing(db, NotePad.Notes.COLUMN_NAME_DELETED, "INTEGER DEFAULT 0");
                addColumnIfMissing(db, NotePad.Notes.COLUMN_NAME_DELETED_AT, "INTEGER DEFAULT 0");
            }
            if (oldVersion < 4) {
                // notes.category 里已有 default/work/...，这里只补 tags 表与默认标签
                createTagsTable(db);
                seedDefaultTags(db);
            }
        }

        private void createNotesTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '" + NoteCategory.DEFAULT + "',"
                    + NotePad.Notes.COLUMN_NAME_PINNED + " INTEGER DEFAULT 0,"
                    + NotePad.Notes.COLUMN_NAME_DELETED + " INTEGER DEFAULT 0,"
                    + NotePad.Notes.COLUMN_NAME_DELETED_AT + " INTEGER DEFAULT 0"
                    + ");");

            // 列表页按「删除标记 + 置顶 + 修改时间」查询，建立索引提升检索速度
            db.execSQL("CREATE INDEX idx_notes_list ON " + TABLE + " ("
                    + NotePad.Notes.COLUMN_NAME_DELETED + ", "
                    + NotePad.Notes.COLUMN_NAME_PINNED + ", "
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + ");");
        }

        private void createTagsTable(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TAG_TABLE + " ("
                    + NotePad.Tags.COLUMN_TAG_KEY + " TEXT PRIMARY KEY NOT NULL,"
                    + NotePad.Tags.COLUMN_LABEL + " TEXT NOT NULL,"
                    + NotePad.Tags.COLUMN_COLOR + " INTEGER NOT NULL,"
                    + NotePad.Tags.COLUMN_CREATED + " INTEGER NOT NULL"
                    + ");");
        }

        /** 写入 5 个默认标签；已存在的 key 会被跳过，可重复执行。 */
        private void seedDefaultTags(SQLiteDatabase db) {
            long now = System.currentTimeMillis();
            for (int i = 0; i < NoteCategory.KEYS.length; i++) {
                String key = NoteCategory.KEYS[i];
                ContentValues values = new ContentValues();
                values.put(NotePad.Tags.COLUMN_TAG_KEY, key);
                values.put(NotePad.Tags.COLUMN_LABEL, mContext.getString(NoteCategory.labelRes(key)));
                values.put(NotePad.Tags.COLUMN_COLOR,
                        ContextCompat.getColor(mContext, NoteCategory.colorRes(key)));
                values.put(NotePad.Tags.COLUMN_CREATED, now + i);
                db.insertWithOnConflict(TAG_TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }

        /** 先用 PRAGMA 判断列是否存在，避免重复 ALTER 抛异常。 */
        private static void addColumnIfMissing(SQLiteDatabase db, String column, String definition) {
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("PRAGMA table_info(" + TABLE + ")", null);
                int nameIndex = cursor.getColumnIndexOrThrow("name");
                while (cursor.moveToNext()) {
                    if (column.equals(cursor.getString(nameIndex))) {
                        return;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column + " " + definition);
        }
    }
}
