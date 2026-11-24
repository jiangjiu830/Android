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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * NotePad 的“契约类”：约定 ContentProvider 的 URI、MIME 类型和数据库列名。
 * <p>
 * 客户端（Activity、Repository）只依赖本类中的常量，不直接接触 SQL，
 * 这样 Provider 内部实现变化时界面代码无需修改。
 */
public final class NotePad {

    /** Provider 的授权标识，必须与 AndroidManifest.xml 中 provider 的 authorities 一致 */
    public static final String AUTHORITY = "com.example.android.notepad";

    private NotePad() {
    }

    /**
     * 笔记表的字段约定（在原版 NotePad 的 title / note / created / modified 基础上，
     * 扩展了 category、pinned、deleted、deleted_at 四个字段）。
     */
    public static final class Notes implements BaseColumns {

        private Notes() {
        }

        /** 表名 */
        public static final String TABLE_NAME = "notes";

        private static final String SCHEME = "content://";
        private static final String PATH_NOTES = "/notes";
        private static final String PATH_NOTE_ID = "/notes/";

        /** 笔记 id 在 URI 路径中的位置 */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /** 笔记列表 URI */
        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /** 单条笔记 URI 的前缀，需要自行追加 id */
        public static final Uri CONTENT_ID_URI_BASE = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /** 单条笔记 URI 的匹配模式 */
        public static final Uri CONTENT_ID_URI_PATTERN =
                Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /** 笔记集合的 MIME 类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.notepad.note";

        /** 单条笔记的 MIME 类型 */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.notepad.note";

        /** 默认排序：置顶优先，然后按修改时间倒序 */
        public static final String DEFAULT_SORT_ORDER =
                "pinned DESC, modified DESC";

        /** 标题，TEXT */
        public static final String COLUMN_NAME_TITLE = "title";

        /** 正文，TEXT */
        public static final String COLUMN_NAME_NOTE = "note";

        /** 创建时间，INTEGER（毫秒时间戳） */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** 修改时间，INTEGER（毫秒时间戳） */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /** 分类，TEXT（default / work / life / study / idea） */
        public static final String COLUMN_NAME_CATEGORY = "category";

        /** 是否置顶，INTEGER（0 / 1） */
        public static final String COLUMN_NAME_PINNED = "pinned";

        /** 是否已删除（回收站），INTEGER（0 / 1） */
        public static final String COLUMN_NAME_DELETED = "deleted";

        /** 移入回收站的时间，INTEGER（毫秒时间戳，0 表示未删除） */
        public static final String COLUMN_NAME_DELETED_AT = "deleted_at";
    }

    /**
     * 标签表的字段与 URI 约定。
     * <p>
     * notes.category 保存的是标签的 {@code tag_key}；标签名称（label）与颜色存在本表，
     * 因此用户重命名标签时不需要批量更新笔记。
     */
    public static final class Tags {

        private Tags() {
        }

        /** 表名 */
        public static final String TABLE_NAME = "tags";

        /** 标签集合 URI */
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/tags");

        /** 标签集合的 MIME 类型 */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.notepad.tag";

        /** 默认排序：按创建顺序，保证筛选条顺序稳定 */
        public static final String DEFAULT_SORT_ORDER = "created ASC, tag_key ASC";

        /** 稳定主键，笔记通过它引用标签 */
        public static final String COLUMN_TAG_KEY = "tag_key";

        /** 用户可见的标签名称，可重命名 */
        public static final String COLUMN_LABEL = "label";

        /** 标签颜色（ARGB） */
        public static final String COLUMN_COLOR = "color";

        /** 创建时间（毫秒时间戳） */
        public static final String COLUMN_CREATED = "created";
    }
}
