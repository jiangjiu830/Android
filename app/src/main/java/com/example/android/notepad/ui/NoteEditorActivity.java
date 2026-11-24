package com.example.android.notepad.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.widget.NestedScrollView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.data.NoteCategory;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.data.Tag;
import com.example.android.notepad.util.Async;
import com.example.android.notepad.util.BackupUtils;
import com.example.android.notepad.util.Prefs;
import com.example.android.notepad.util.TimeUtils;
import com.example.android.notepad.util.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;

/**
 * 笔记编辑器：新建 / 编辑笔记。
 * <p>
 * 交互设计参考原版 NotePad 的「自动保存」：离开界面（返回键、切换应用）时
 * 自动写入数据库；同时提供显式的「保存」按钮让用户确认。
 * 扩展功能：标签选择、置顶、字数统计、分享。
 */
public class NoteEditorActivity extends BaseActivity {

    private static final String TAG = "NoteEditorActivity";

    /** 传入笔记 id 表示编辑已有笔记，不传表示新建 */
    public static final String EXTRA_NOTE_ID = "extra_note_id";

    private static final String STATE_NOTE_ID = "state_note_id";

    private MaterialToolbar mToolbar;
    private TextInputEditText mTitleEdit;
    private TextInputEditText mContentEdit;
    private ChipGroup mTagGroup;
    private TextView mMeta;
    private TextView mWordCount;

    private NoteRepository mRepository;

    /** 当前笔记 id，-1 表示尚未保存的新笔记 */
    private long mNoteId = -1L;
    private boolean mIsNew = true;
    private boolean mLoaded;
    private boolean mDirty;
    private boolean mSuppressTagCallback;

    /** 当前标签 key；保存时写入 notes.category */
    private String mTagKey = NoteCategory.DEFAULT;
    private List<Tag> mTags = Collections.emptyList();
    private boolean mPinned;
    private long mCreated;
    private long mModified;

    /** 初始化编辑器：恢复笔记 id，加载内容，注册自动保存与返回保存。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_editor);

        mRepository = NoteRepository.get(this);

        mToolbar = findViewById(R.id.toolbar);
        mTitleEdit = findViewById(R.id.edit_title);
        mContentEdit = findViewById(R.id.edit_content);
        mTagGroup = findViewById(R.id.category_group);
        mMeta = findViewById(R.id.text_meta);
        mWordCount = findViewById(R.id.text_word_count);

        setupToolbar(mToolbar, true);
        loadTags();

        // 正文字号跟随设置
        mContentEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, Prefs.fontSize(this));

        WindowInsetsHelper.padTop(findViewById(R.id.app_bar));
        WindowInsetsHelper.padBottomForIme((NestedScrollView) findViewById(R.id.scroll));
        applySystemBarAppearance();

        // 恢复旋转前的笔记 id，避免横竖屏切换后变成新建笔记
        long noteId = savedInstanceState == null
                ? -1L
                : savedInstanceState.getLong(STATE_NOTE_ID, -1L);
        if (noteId <= 0) {
            noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        }

        if (noteId > 0) {
            mNoteId = noteId;
            mIsNew = false;
            loadNote();
        } else {
            mLoaded = true;
            updateTitle();
            updateMeta();
            updateWordCount();
            mContentEdit.requestFocus();
        }

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mDirty = true;
                updateWordCount();
            }
        };
        mTitleEdit.addTextChangedListener(watcher);
        mContentEdit.addTextChangedListener(watcher);

        // 返回键先保存再退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveIfNeeded();
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从标签管理页返回时刷新标签条
        loadTags();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 与 NotePad 原版一致：离开界面时自动保存
        saveIfNeeded();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mNoteId > 0) {
            outState.putLong(STATE_NOTE_ID, mNoteId);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        saveIfNeeded();
        finish();
        return true;
    }

    /* ===================== 初始化 ===================== */

    /** 加载标签库，并重建编辑器里的标签 Chip。 */
    private void loadTags() {
        Async.run(mRepository::tags, tags -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            mTags = tags == null ? Collections.emptyList() : tags;
            if (tagIndex(mTagKey) < 0) {
                mTagKey = NoteCategory.DEFAULT;
            }
            setupTagChips();
        });
    }

    /** 标签 Chip：默认选中当前笔记的标签。 */
    private void setupTagChips() {
        CharSequence[] labels = new CharSequence[mTags.size()];
        for (int i = 0; i < mTags.size(); i++) {
            labels[i] = mTags.get(i).label;
        }
        int checked = Math.max(0, tagIndex(mTagKey));
        Chips.populate(mTagGroup, labels, checked, index -> {
            // 笔记内容尚未加载完成时先不响应，避免把空内容当成用户修改
            if (!mLoaded || mSuppressTagCallback || index < 0 || index >= mTags.size()) {
                return;
            }
            mTagKey = mTags.get(index).key;
            mDirty = true;
        });
    }

    /** 程序化选中标签，用标记避免触发「内容已修改」。 */
    private void selectTagChip(String key) {
        int index = tagIndex(key);
        if (index < 0) {
            return;
        }
        mSuppressTagCallback = true;
        Chips.select(mTagGroup, index);
        mSuppressTagCallback = false;
    }

    private int tagIndex(String key) {
        for (int i = 0; i < mTags.size(); i++) {
            if (mTags.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** 后台读取笔记；若用户已在输入（mDirty），则不覆盖当前内容。 */
    private void loadNote() {
        final long noteId = mNoteId;
        Async.run(() -> mRepository.find(noteId), note -> {
            if (note == null) {
                // 笔记可能已被删除
                finish();
                return;
            }
            mLoaded = true;
            mPinned = note.pinned;
            mTagKey = note.category;
            // 标签库可能还没加载完；此时先保留标签 key，等标签到达后再选中
            if (!mTags.isEmpty() && tagIndex(mTagKey) < 0) {
                mTagKey = NoteCategory.DEFAULT;
            }
            mCreated = note.created;
            mModified = note.modified;

            if (!mDirty) {
                mTitleEdit.setText(note.title);
                mContentEdit.setText(note.content);
                selectTagChip(mTagKey);
                mDirty = false;
            }

            updateTitle();
            updateMeta();
            updateWordCount();
            invalidateOptionsMenu();
        });
    }

    /* ===================== 保存 ===================== */

    /**
     * 保存笔记。同步写入数据库——写一条 SQLite 记录只有几毫秒，
     * 这样能保证界面退出前数据一定落盘。
     *
     * @return 是否真的写入了数据
     */
    private boolean saveIfNeeded() {
        if (!mLoaded || !mDirty) {
            return false;
        }

        String title = textOf(mTitleEdit).trim();
        String content = textOf(mContentEdit);
        if (mIsNew && title.isEmpty() && content.trim().isEmpty()) {
            // 新建后什么都没写，直接丢弃，避免出现空笔记
            mDirty = false;
            return false;
        }

        long now = System.currentTimeMillis();
        Note note = new Note();
        note.id = mNoteId;
        note.title = title;
        note.content = content;
        note.category = mTagKey;
        note.pinned = mPinned;
        note.created = mCreated > 0 ? mCreated : now;
        note.modified = now;

        try {
            if (mIsNew) {
                long id = mRepository.insert(note);
                if (id <= 0) {
                    throw new IllegalStateException("插入笔记失败");
                }
                mNoteId = id;
                mIsNew = false;
                mCreated = note.created;
            } else {
                mRepository.update(note);
            }
            mModified = now;
            mDirty = false;
            updateTitle();
            updateMeta();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "保存笔记失败", e);
            Toast.makeText(this, R.string.toast_save_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /* ===================== 界面状态 ===================== */

    /** 根据 mIsNew 切换标题：新建 / 编辑。 */
    private void updateTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mIsNew ? R.string.editor_new_title : R.string.editor_edit_title);
        }
    }

    /** 底部完整时间：创建时间 + 最近修改时间。 */
    private void updateMeta() {
        if (mCreated <= 0) {
            mMeta.setText("");
            return;
        }
        long modified = mModified > 0 ? mModified : mCreated;
        mMeta.setText(getString(R.string.editor_meta,
                TimeUtils.formatDateTime(mCreated), TimeUtils.formatDateTime(modified)));
    }

    /** 字数统计：只计非空白字符，中文与英文都按字符数算。 */
    private void updateWordCount() {
        String content = textOf(mContentEdit);
        int count = 0;
        for (int i = 0; i < content.length(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) {
                count++;
            }
        }
        mWordCount.setText(getString(R.string.editor_word_count, count));
    }

    /* ===================== 菜单 ===================== */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem pinItem = menu.findItem(R.id.action_pin);
        if (pinItem != null) {
            pinItem.setTitle(mPinned ? R.string.note_menu_unpin : R.string.note_menu_pin);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            toast(saveIfNeeded() ? R.string.toast_saved : R.string.toast_nothing_to_save);
            return true;
        } else if (id == R.id.action_pin) {
            togglePin();
            return true;
        } else if (id == R.id.action_tags) {
            startActivity(new Intent(this, TagActivity.class));
            return true;
        } else if (id == R.id.action_share) {
            shareNote();
            return true;
        } else if (id == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 切换置顶；已有笔记立即落库，新笔记等退出时随保存一起写入。 */
    private void togglePin() {
        mPinned = !mPinned;
        mDirty = true;
        invalidateOptionsMenu();
        if (!mIsNew) {
            saveIfNeeded();
        }
        toast(mPinned ? R.string.toast_pinned : R.string.toast_unpinned);
    }

    /** 分享当前编辑器中的内容，不要求先保存。 */
    private void shareNote() {
        String title = textOf(mTitleEdit).trim();
        String content = textOf(mContentEdit);
        if (title.isEmpty() && content.trim().isEmpty()) {
            toast(R.string.toast_nothing_to_share);
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject,
                title.isEmpty() ? getString(R.string.note_untitled) : title));
        send.putExtra(Intent.EXTRA_TEXT, BackupUtils.toShareText(title, content));
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    /** 删除前确认；确认后进入回收站，而不是直接删库。 */
    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_confirm_ok, (dialog, which) -> deleteNote())
                .show();
    }

    /** 新笔记直接丢弃；已有笔记做软删除后退出。 */
    private void deleteNote() {
        if (mIsNew) {
            // 未保存的新笔记直接丢弃，不再触发 onPause 自动保存
            mLoaded = false;
            mDirty = false;
            finish();
            return;
        }

        final long noteId = mNoteId;
        mLoaded = false;
        mDirty = false;
        Async.run(() -> mRepository.setDeleted(noteId, true), rows -> {
            toast(R.string.toast_moved_to_trash);
            finish();
        });
    }

    /* ===================== 工具 ===================== */

    /** 读取 TextView 文本，null 统一当作空串，省去调用处判空。 */
    private static String textOf(TextView view) {
        CharSequence text = view == null ? null : view.getText();
        return text == null ? "" : text.toString();
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}
