"""生成演示用的 SQLite 数据库（仅用于截图演示，不影响应用功能）。

用法：
    python seed_demo_db.py note_pad.db
然后把生成的 note_pad.db 推送到设备的
    /data/data/com.example.android.notepad/databases/note_pad.db
（需要 run-as，只有 debug 包可以这样做）
"""

import os
import sqlite3
import sys
import time

NOW = int(time.time() * 1000)
MINUTE = 60 * 1000
HOUR = 60 * MINUTE
DAY = 24 * HOUR

# 默认标签 + 一个自定义示例标签：key / 显示名 / ARGB 颜色
TAGS = [
    ("default", "默认", 0xFF7A8794),
    ("work", "工作", 0xFF3D7EFF),
    ("life", "生活", 0xFFE2724B),
    ("study", "学习", 0xFF2F9E6E),
    ("idea", "灵感", 0xFFB05CA8),
    ("tag_course", "课程", 0xFF2C8C99),
]

NOTES = [
    (
        "期中实验进度",
        "1. NotePad 源码分析 ✔\n"
        "2. 列表时间戳显示 ✔\n"
        "3. 按标题 / 内容搜索 ✔\n"
        "4. 分类筛选与置顶 ✔\n"
        "5. 备份恢复、深色主题、回收站\n\n"
        "下周一前提交实验报告和演示视频。",
        "work",
        1,
        NOW - 25 * MINUTE,
        NOW - 25 * MINUTE,
    ),
    (
        "Android 数据存储要点",
        "SQLite 由 SQLiteOpenHelper 管理建表与版本升级；\n"
        "ContentProvider 对外提供 content:// 接口，"
        "配合 ContentResolver 完成增删改查；\n"
        "查询结果用 Cursor 遍历，记得在 finally 中关闭，避免泄漏。",
        "tag_course",
        0,
        NOW - 3 * HOUR,
        NOW - 2 * HOUR,
    ),
    (
        "周末采购清单",
        "牛奶 2 盒\n全麦面包\n鸡蛋 30 枚\n西兰花、胡萝卜\n洗衣液\n\n"
        "顺路取一下干洗的外套。",
        "life",
        0,
        NOW - DAY - 2 * HOUR,
        NOW - DAY - 90 * MINUTE,
    ),
    (
        "产品灵感：便签式首页",
        "把最近 3 条笔记做成便签贴在首页，左右滑动切换，点击直接进入编辑；"
        "便签底色跟随笔记分类，置顶的笔记固定在第一张。",
        "idea",
        0,
        NOW - 3 * DAY,
        NOW - 3 * DAY + 40 * MINUTE,
    ),
    (
        "随手记",
        "地铁 10 号线下班高峰太挤了，考虑提前半小时走。",
        "default",
        0,
        NOW - 5 * DAY,
        NOW - 5 * DAY,
    ),
    (
        "年初会议纪要",
        "确定上半年三个迭代目标：\n"
        "1. 移动端优先完成记事本应用的功能扩展；\n"
        "2. 补齐单元测试与崩溃监控；\n"
        "3. 一季度末做一次用户访谈。",
        "work",
        0,
        NOW - 255 * DAY,
        NOW - 255 * DAY,
    ),
]


def build(path):
    # 每次都重新生成，避免旧文件里的 notes 表导致建表失败
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    cursor = connection.cursor()
    cursor.executescript(
        """
        PRAGMA user_version = 4;

        CREATE TABLE notes (
            _id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT,
            note TEXT,
            created INTEGER,
            modified INTEGER,
            category TEXT DEFAULT 'default',
            pinned INTEGER DEFAULT 0,
            deleted INTEGER DEFAULT 0,
            deleted_at INTEGER DEFAULT 0
        );

        CREATE INDEX idx_notes_list ON notes (deleted, pinned, modified);

        CREATE TABLE tags (
            tag_key TEXT PRIMARY KEY NOT NULL,
            label TEXT NOT NULL,
            color INTEGER NOT NULL,
            created INTEGER NOT NULL
        );

        CREATE TABLE android_metadata (locale TEXT);
        INSERT INTO android_metadata (locale) VALUES ('zh_CN');
        """
    )

    for index, (key, label, color) in enumerate(TAGS):
        cursor.execute(
            "INSERT INTO tags (tag_key, label, color, created) VALUES (?, ?, ?, ?)",
            (key, label, color, NOW + index),
        )

    for title, content, category, pinned, created, modified in NOTES:
        cursor.execute(
            "INSERT INTO notes (title, note, created, modified, category, pinned, deleted, deleted_at)"
            " VALUES (?, ?, ?, ?, ?, ?, 0, 0)",
            (title, content, created, modified, category, pinned),
        )

    connection.commit()
    cursor.close()
    connection.close()
    print("wrote", path, "with", len(NOTES), "notes")


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "note_pad.db")
