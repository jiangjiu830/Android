package com.example.android.notepad.ui;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.data.SortOrder;
import com.example.android.notepad.data.Tag;
import com.example.android.notepad.util.Async;
import com.example.android.notepad.util.BackupUtils;
import com.example.android.notepad.util.Prefs;
import com.example.android.notepad.util.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * 笔记列表界面（应用入口）。
 * <p>
 * 对应实验的基本要求：
 * <ul>
 *     <li>列表项显示修改时间戳（今天 / 昨天 / 日期）；</li>
 *     <li>顶部搜索框按标题或正文实时查询，并高亮关键字。</li>
 * </ul>
 * 扩展功能：标签筛选、置顶、排序、滑动删除（回收站）、备份与恢复、深色主题。
 */
public class NoteListActivity extends BaseActivity implements NoteAdapter.Listener {

    /** 搜索输入防抖时间，避免每输入一个字符就查询一次数据库 */
    private static final long SEARCH_DELAY_MS = 280L;

    /** 旋转屏幕 / 进程重建时保存的筛选状态 */
    private static final String STATE_KEYWORD = "state_keyword";
    private static final String STATE_TAG = "state_tag";

    private MaterialToolbar mToolbar;
    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private TextView mEmptyTitle;
    private TextView mEmptyDesc;
    private ChipGroup mTagGroup;
    private FloatingActionButton mFab;

    private NoteAdapter mAdapter;
    private NoteRepository mRepository;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPendingSearch;

    /** 当前搜索关键字（空串表示不搜索） */
    private String mKeyword = "";
    /** 当前标签 key（null 表示全部） */
    private String mTagKey;
    /** 标签库，用于动态生成筛选条 */
    private List<Tag> mTags = Collections.emptyList();
    private SortOrder mSortOrder;
    private MenuItem mSearchItem;

    /** 查询序号，用于丢弃过期的异步结果 */
    private long mQueryToken;

    /** 备份文件保存位置选择器（系统文件选择器，无需存储权限） */
    private final ActivityResultLauncher<String> mCreateBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(BackupUtils.MIME_TYPE),
                    this::onBackupTargetSelected);

    /** 备份文件选择器 */
    private final ActivityResultLauncher<String[]> mOpenBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    this::onRestoreFileSelected);

    /** 初始化列表页：恢复筛选状态、标签条、列表、搜索与边到边内边距。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_list);

        mRepository = NoteRepository.get(this);
        mSortOrder = Prefs.sortOrder(this);

        // 恢复旋转前的搜索关键字与标签筛选，避免重建后筛选条件丢失
        if (savedInstanceState != null) {
            mKeyword = savedInstanceState.getString(STATE_KEYWORD, "");
            mTagKey = savedInstanceState.getString(STATE_TAG);
        }

        mToolbar = findViewById(R.id.toolbar);
        mRecyclerView = findViewById(R.id.recycler);
        mEmptyView = findViewById(R.id.empty_view);
        mEmptyTitle = findViewById(R.id.empty_title);
        mEmptyDesc = findViewById(R.id.empty_desc);
        mTagGroup = findViewById(R.id.category_group);
        mFab = findViewById(R.id.fab_add);

        setupToolbar(mToolbar, false);
        setupTagChips(Collections.emptyList());
        setupRecyclerView();

        mFab.setOnClickListener(view -> openEditor(-1L));

        // 返回键：搜索框展开时先收起搜索框，否则退出应用
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mSearchItem != null && mSearchItem.isActionViewExpanded()) {
                    mSearchItem.collapseActionView();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        // 边到边布局：状态栏内边距、导航栏内边距
        WindowInsetsHelper.padTop(findViewById(R.id.app_bar));
        WindowInsetsHelper.padBottom(mRecyclerView);
        WindowInsetsHelper.marginBottom(mFab);
        applySystemBarAppearance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页 / 标签管理 / 设置页返回时同步设置并刷新标签与列表
        mSortOrder = Prefs.sortOrder(this);
        mAdapter.setShowPreview(Prefs.showPreview(this));
        mAdapter.setPreviewTextSize(Prefs.fontSize(this));
        loadTags();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPendingSearch != null) {
            mHandler.removeCallbacks(mPendingSearch);
            mPendingSearch = null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 搜索框与标签筛选属于界面状态，旋转重建后应保持
        outState.putString(STATE_KEYWORD, mKeyword);
        outState.putString(STATE_TAG, mTagKey);
    }

    /* ===================== 列表与标签 ===================== */

    /** 加载标签库并重建筛选条；标签被删除时自动回到「全部」。 */
    private void loadTags() {
        Async.run(mRepository::tags, tags -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            mTags = tags == null ? Collections.emptyList() : tags;
            if (mTagKey != null && findTagByKey(mTagKey) == null) {
                mTagKey = null;
            }
            setupTagChips(mTags);
            refresh();
        });
    }

    /** 标签筛选条：第 0 项为「全部」，其余按标签创建顺序生成。 */
    private void setupTagChips(List<Tag> tags) {
        final String[] keys = new String[tags.size() + 1];
        CharSequence[] labels = new CharSequence[keys.length];

        keys[0] = null;
        labels[0] = getString(R.string.category_all);
        for (int i = 0; i < tags.size(); i++) {
            keys[i + 1] = tags.get(i).key;
            labels[i + 1] = tags.get(i).label;
        }

        int checkedIndex = 0;
        if (mTagKey != null) {
            for (int i = 0; i < tags.size(); i++) {
                if (mTagKey.equals(tags.get(i).key)) {
                    checkedIndex = i + 1;
                    break;
                }
            }
        }

        Chips.populate(mTagGroup, labels, checkedIndex, index -> {
            if (index < 0 || index >= keys.length) {
                return;
            }
            mTagKey = keys[index];
            refresh();
        });
    }

    private Tag findTagByKey(String key) {
        for (Tag tag : mTags) {
            if (tag.key.equals(key)) {
                return tag;
            }
        }
        return null;
    }

    /** 配置列表与左滑手势：左滑只做软删除，误删可由 Snackbar 撤销。 */
    private void setupRecyclerView() {
        mAdapter = new NoteAdapter(this, this);
        mAdapter.setKeyword(mKeyword);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        // 向左滑动 = 移入回收站（随后可用 Snackbar 撤销）
        ItemTouchHelper swipeHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView,
                                          @NonNull RecyclerView.ViewHolder viewHolder,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        int position = viewHolder.getBindingAdapterPosition();
                        Note note = position == RecyclerView.NO_POSITION
                                ? null : mAdapter.noteAt(position);
                        if (note != null) {
                            moveToTrash(note, false);
                        }
                    }
                });
        swipeHelper.attachToRecyclerView(mRecyclerView);
    }

    /** 重新查询并刷新列表（在后台线程执行查询） */
    private void refresh() {
        final long token = ++mQueryToken;
        final String keyword = mKeyword;
        final String tagKey = mTagKey;
        final SortOrder sort = mSortOrder;

        Async.run(() -> mRepository.query(false, keyword, tagKey, sort), notes -> {
            if (token != mQueryToken || isFinishing() || isDestroyed()) {
                return;
            }
            List<Note> list = notes == null ? Collections.emptyList() : notes;
            mAdapter.submit(list);
            updateEmptyState(list.isEmpty());
            updateSubtitle(list.size());
        });
    }

    /** 空列表文案要区分「还没有笔记」和「筛选/搜索无结果」。 */
    private void updateEmptyState(boolean empty) {
        mRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        mEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        boolean filtered = !TextUtils.isEmpty(mKeyword) || mTagKey != null;
        mEmptyTitle.setText(filtered ? R.string.empty_search_title : R.string.empty_title);
        mEmptyDesc.setText(filtered ? R.string.empty_search_desc : R.string.empty_desc);
    }

    /** 标题栏副标题：当前条数 + 排序方式。 */
    private void updateSubtitle(int count) {
        String countText;
        if (!TextUtils.isEmpty(mKeyword)) {
            countText = getString(R.string.list_subtitle_search, count);
        } else if (mTagKey != null) {
            countText = getString(R.string.list_subtitle_filtered, count);
        } else {
            countText = getString(R.string.list_subtitle, count);
        }
        mToolbar.setSubtitle(countText + " · " + getString(mSortOrder.labelRes()));
    }

    /* ===================== 条目操作 ===================== */

    /** 点击条目：进入编辑器。 */
    @Override
    public void onNoteClick(Note note) {
        openEditor(note.id);
    }

    /** 长按条目：弹出置顶 / 移入回收站 / 分享。 */
    @Override
    public void onNoteLongClick(final Note note) {
        final CharSequence[] actions = {
                getString(note.pinned ? R.string.note_menu_unpin : R.string.note_menu_pin),
                getString(R.string.note_menu_delete),
                getString(R.string.note_menu_share)
        };

        new MaterialAlertDialogBuilder(this)
                .setTitle(note.displayTitle(this))
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            togglePin(note);
                            break;
                        case 1:
                            moveToTrash(note, true);
                            break;
                        case 2:
                            shareNote(note);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    /** 取反置顶状态并写库，刷新后由 SQL 排序把置顶项排到最前。 */
    private void togglePin(final Note note) {
        final boolean pinned = !note.pinned;
        Async.run(() -> mRepository.setPinned(note.id, pinned), rows -> {
            refresh();
            showMessage(pinned ? R.string.toast_pinned : R.string.toast_unpinned);
        });
    }

    /** askConfirm=true 用于长按菜单；左滑删除不弹框，靠 Snackbar 撤销兜底。 */
    private void moveToTrash(final Note note, boolean askConfirm) {
        if (!askConfirm) {
            doMoveToTrash(note);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_confirm_ok,
                        (dialog, which) -> doMoveToTrash(note))
                .show();
    }

    /** 软删除（deleted=1），并给出「撤销」入口。 */
    private void doMoveToTrash(final Note note) {
        Async.run(() -> mRepository.setDeleted(note.id, true), rows -> {
            refresh();
            Snackbar.make(mRecyclerView, R.string.toast_moved_to_trash, Snackbar.LENGTH_LONG)
                    .setAnchorView(mFab)
                    .setAction(R.string.undo, view -> restoreNote(note.id))
                    .show();
        });
    }

    /** 撤销删除：把 deleted 置回 0。 */
    private void restoreNote(final long noteId) {
        Async.run(() -> mRepository.setDeleted(noteId, false), rows -> {
            refresh();
            showMessage(R.string.toast_restored);
        });
    }

    /** 用系统分享面板导出单条笔记的纯文本。 */
    private void shareNote(Note note) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,
                getString(R.string.share_subject, note.displayTitle(this)));
        send.putExtra(Intent.EXTRA_TEXT, BackupUtils.toShareText(note.title, note.content));
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
    }

    /** noteId <= 0 表示新建；否则把 id 传给编辑器加载。 */
    private void openEditor(long noteId) {
        Intent intent = new Intent(this, NoteEditorActivity.class);
        if (noteId > 0) {
            intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId);
        }
        startActivity(intent);
    }

    /* ===================== 菜单 ===================== */

    /** 菜单项：搜索、排序、回收站、备份、设置、关于。 */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_list, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            mSearchItem = searchItem;
            // 统一图标颜色，避免深色 / 浅色主题下看不见
            tintMenuIcon(searchItem);
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        cancelPendingSearch();
                        applyKeyword(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        scheduleSearch(newText);
                        return true;
                    }
                });
            }
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    cancelPendingSearch();
                    applyKeyword("");
                    return true;
                }
            });

            // 旋转重建后重新展开搜索框并回填关键字
            if (!TextUtils.isEmpty(mKeyword)) {
                searchItem.expandActionView();
                SearchView restored = (SearchView) searchItem.getActionView();
                if (restored != null) {
                    restored.setQuery(mKeyword, false);
                    restored.clearFocus();
                }
            }
        }
        return true;
    }

    /** 菜单分发：排序、回收站、标签管理、备份、设置、关于分别处理。 */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_trash) {
            startActivity(new Intent(this, TrashActivity.class));
            return true;
        } else if (id == R.id.action_tags) {
            startActivity(new Intent(this, TagActivity.class));
            return true;
        } else if (id == R.id.action_backup) {
            showBackupDialog();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* ===================== 搜索 ===================== */

    /** 把菜单图标染成当前的 colorOnSurface，跟随深浅色主题 */
    private void tintMenuIcon(MenuItem item) {
        Drawable icon = AppCompatResources.getDrawable(this, R.drawable.ic_search);
        if (icon == null) {
            return;
        }
        Drawable tinted = DrawableCompat.wrap(icon).mutate();
        DrawableCompat.setTint(tinted, MaterialColors.getColor(mToolbar,
                com.google.android.material.R.attr.colorOnSurface, 0xFF191C1A));
        item.setIcon(tinted);
    }

    /** 搜索防抖：输入停止 280ms 后才真正查询，减少无谓的数据库访问。 */
    private void scheduleSearch(final String keyword) {
        cancelPendingSearch();
        mPendingSearch = () -> {
            mPendingSearch = null;
            applyKeyword(keyword);
        };
        mHandler.postDelayed(mPendingSearch, SEARCH_DELAY_MS);
    }

    /** 取消尚未触发的防抖任务（提交、收起搜索框、页面销毁时）。 */
    private void cancelPendingSearch() {
        if (mPendingSearch != null) {
            mHandler.removeCallbacks(mPendingSearch);
            mPendingSearch = null;
        }
    }

    /** 应用关键字；与当前值相同则直接返回，避免重复查询。 */
    private void applyKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (TextUtils.equals(normalized, mKeyword)) {
            return;
        }
        mKeyword = normalized;
        mAdapter.setKeyword(mKeyword);
        refresh();
    }

    /* ===================== 对话框 ===================== */

    /** 单选排序方式，写入偏好后立即刷新。 */
    private void showSortDialog() {
        final SortOrder[] values = SortOrder.values();
        CharSequence[] labels = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(values[i].labelRes());
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(labels, mSortOrder.ordinal(), (dialog, which) -> {
                    mSortOrder = values[which];
                    Prefs.setSortOrder(this, mSortOrder);
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 备份对话框：导出用 CreateDocument，导入用 OpenDocument，均走 SAF。 */
    private void showBackupDialog() {
        CharSequence[] items = {
                getString(R.string.backup_export),
                getString(R.string.backup_import)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_dialog_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        mCreateBackupLauncher.launch(BackupUtils.suggestedFileName(this));
                    } else {
                        mOpenBackupLauncher.launch(new String[]{
                                BackupUtils.MIME_TYPE, "text/plain", "*/*"});
                    }
                })
                .show();
    }

    /** 关于对话框：版本号 + 基础/扩展功能清单。 */
    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(getString(R.string.about_message, versionName()))
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    /* ===================== 备份与恢复 ===================== */

    /** 用户选好保存位置后，在后台把全部笔记与标签写成 JSON。 */
    private void onBackupTargetSelected(final Uri uri) {
        if (uri == null) {
            return;
        }
        Async.run(() -> {
            List<Note> notes = mRepository.queryAll();
            List<Tag> tags = mRepository.tags();
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
                if (outputStream == null) {
                    throw new IOException("无法写入所选文件");
                }
                BackupUtils.write(outputStream, notes, tags);
            }
            return notes.size();
        }, count -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (count == null) {
                showMessage(R.string.backup_failed);
            } else {
                showMessage(getString(R.string.backup_success, count));
            }
        });
    }

    /** 用户选好备份文件后，后台解析标签与笔记并批量导入。 */
    private void onRestoreFileSelected(final Uri uri) {
        if (uri == null) {
            return;
        }
        Async.run(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException("无法读取所选文件");
                }
                return BackupUtils.read(inputStream);
            }
        }, backup -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (backup == null || backup.notes.isEmpty()) {
                showMessage(R.string.import_failed);
                return;
            }
            Async.run(() -> mRepository.importBackup(backup.tags, backup.notes), count -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                int imported = count == null ? 0 : count;
                showMessage(getString(R.string.import_success, imported));
                loadTags();
            });
        });
    }

    /* ===================== 工具 ===================== */

    /** 统一 Snackbar，锚定 FAB 上方避免遮挡。 */
    private void showMessage(String message) {
        Snackbar.make(mRecyclerView, message, Snackbar.LENGTH_LONG)
                .setAnchorView(mFab)
                .show();
    }

    private void showMessage(int messageRes) {
        showMessage(getString(messageRes));
    }
}
