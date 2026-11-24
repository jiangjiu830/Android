package com.example.android.notepad.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.util.Async;
import com.example.android.notepad.util.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;

/**
 * 回收站：查看被删除的笔记，可以恢复或彻底删除。
 * <p>
 * 这是「软删除」的落地：笔记在数据库里只是把 deleted 置为 1，
 * 因此误删后可以随时找回。
 */
public class TrashActivity extends BaseActivity implements TrashAdapter.Listener {

    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private TrashAdapter mAdapter;
    private NoteRepository mRepository;
    private MenuItem mClearItem;

    /** 初始化回收站列表、空状态与工具栏。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);

        mRepository = NoteRepository.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        mRecyclerView = findViewById(R.id.recycler);
        mEmptyView = findViewById(R.id.empty_view);

        setupToolbar(toolbar, true);

        mAdapter = new TrashAdapter(this, this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        WindowInsetsHelper.padTop(findViewById(R.id.app_bar));
        WindowInsetsHelper.padBottom(mRecyclerView);
        applySystemBarAppearance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** 后台查询回收站，并同步空状态与「清空」菜单的可用性。 */
    private void refresh() {
        Async.run(mRepository::queryTrash, notes -> {
            if (isFinishing()) {
                return;
            }
            List<Note> list = notes == null ? Collections.emptyList() : notes;
            mAdapter.submit(list);
            mRecyclerView.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
            mEmptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            if (mClearItem != null) {
                mClearItem.setEnabled(!list.isEmpty());
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_trash, menu);
        mClearItem = menu.findItem(R.id.action_clear_trash);
        if (mClearItem != null) {
            mClearItem.setEnabled(mAdapter.getItemCount() > 0);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_trash) {
            confirmClearTrash();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 恢复：把 deleted 置回 0，笔记重新出现在列表。 */
    @Override
    public void onRestore(final Note note) {
        Async.run(() -> mRepository.setDeleted(note.id, false), rows -> {
            refresh();
            showMessage(R.string.toast_restored);
        });
    }

    /** 彻底删除：二次确认后执行 DELETE，不可恢复。 */
    @Override
    public void onDeleteForever(final Note note) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(note.displayTitle(this))
                .setMessage(R.string.delete_forever_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_delete_forever, (dialog, which) ->
                        Async.run(() -> mRepository.deletePermanently(note.id), rows -> {
                            refresh();
                            showMessage(R.string.toast_deleted);
                        }))
                .show();
    }

    /** 清空回收站：确认后一次删除所有 deleted=1 的记录。 */
    private void confirmClearTrash() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_clear_trash)
                .setMessage(R.string.clear_trash_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) ->
                        Async.run(mRepository::emptyTrash, rows -> {
                            refresh();
                            showMessage(R.string.toast_trash_cleared);
                        }))
                .show();
    }

    private void showMessage(int messageRes) {
        Snackbar.make(mRecyclerView, messageRes, Snackbar.LENGTH_SHORT).show();
    }
}
