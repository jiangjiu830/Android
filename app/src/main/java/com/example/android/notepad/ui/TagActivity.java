package com.example.android.notepad.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.NoteCategory;
import com.example.android.notepad.data.NoteRepository;
import com.example.android.notepad.data.Tag;
import com.example.android.notepad.util.Async;
import com.example.android.notepad.util.WindowInsetsHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;
import java.util.List;

/**
 * 标签管理页：查看全部标签，并支持新建、重命名、删除。
 * <p>
 * 删除标签时，使用该标签的笔记会自动归入「默认」标签，避免出现无标签的笔记。
 */
public class TagActivity extends BaseActivity implements TagAdapter.Listener {

    private RecyclerView mRecyclerView;
    private TagAdapter mAdapter;
    private NoteRepository mRepository;
    private List<Tag> mTags = Collections.emptyList();

    /** 初始化标签列表、工具栏与新建按钮。 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        mRepository = NoteRepository.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        mRecyclerView = findViewById(R.id.recycler);
        FloatingActionButton fab = findViewById(R.id.fab_add);

        setupToolbar(toolbar, true);
        toolbar.setSubtitle(R.string.tag_manage_hint);

        mAdapter = new TagAdapter(this);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mAdapter);

        fab.setOnClickListener(view -> showNameDialog(null));

        WindowInsetsHelper.padTop(findViewById(R.id.app_bar));
        WindowInsetsHelper.padBottom(mRecyclerView);
        WindowInsetsHelper.marginBottom(fab);
        applySystemBarAppearance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
    }

    /** 后台读取标签并刷新列表。 */
    private void loadTags() {
        Async.run(mRepository::tags, tags -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            mTags = tags == null ? Collections.emptyList() : tags;
            mAdapter.submit(mTags);
        });
    }

    @Override
    public void onRename(@NonNull Tag tag) {
        showNameDialog(tag.key);
    }

    @Override
    public void onDelete(@NonNull Tag tag) {
        confirmDelete(tag);
    }

    /**
     * 新建或重命名标签的共用对话框。
     *
     * @param editKey null 表示新建，否则为重命名的标签 key
     */
    private void showNameDialog(final String editKey) {
        final boolean adding = editKey == null;
        View content = getLayoutInflater().inflate(R.layout.dialog_tag_name, null);
        final TextInputLayout inputLayout = content.findViewById(R.id.layout_tag_name);
        final TextInputEditText edit = content.findViewById(R.id.edit_tag_name);

        if (!adding) {
            Tag editing = findTag(editKey);
            if (editing != null) {
                edit.setText(editing.label);
                edit.setSelection(edit.length());
            }
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(adding ? R.string.tag_add_title : R.string.tag_rename_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String name = textOf(edit).trim();
                    if (name.isEmpty()) {
                        inputLayout.setError(getString(R.string.tag_name_empty));
                        return;
                    }
                    if (name.length() > 16) {
                        inputLayout.setError(getString(R.string.tag_name_too_long));
                        return;
                    }
                    if (labelExists(name, editKey)) {
                        inputLayout.setError(getString(R.string.tag_name_duplicate));
                        return;
                    }
                    dialog.dismiss();
                    if (adding) {
                        addTag(name);
                    } else {
                        renameTag(editKey, name);
                    }
                }));
        dialog.show();
    }

    private void addTag(String label) {
        Async.run(() -> mRepository.addTag(label), tag -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (tag == null) {
                toast(R.string.tag_name_duplicate);
                return;
            }
            toast(R.string.tag_added);
            loadTags();
        });
    }

    private void renameTag(String key, String label) {
        Async.run(() -> mRepository.renameTag(key, label), rows -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (rows == null || rows == 0) {
                toast(R.string.tag_name_duplicate);
                return;
            }
            toast(R.string.tag_renamed);
            loadTags();
        });
    }

    private void confirmDelete(final Tag tag) {
        if (NoteCategory.DEFAULT.equals(tag.key)) {
            toast(R.string.tag_delete_default);
            return;
        }
        Async.run(() -> mRepository.countNotesWithTag(tag.key), count -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            int notes = count == null ? 0 : count;
            String message = notes > 0
                    ? getString(R.string.tag_delete_confirm_in_use, tag.label, notes)
                    : getString(R.string.tag_delete_confirm, tag.label);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.tag_delete_title)
                    .setMessage(message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.tag_delete, (dialog, which) -> deleteTag(tag))
                    .show();
        });
    }

    private void deleteTag(final Tag tag) {
        Async.run(() -> mRepository.deleteTag(tag.key), rows -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (rows == null || rows <= 0) {
                toast(R.string.tag_delete_default);
                return;
            }
            toast(R.string.tag_deleted);
            loadTags();
        });
    }

    private Tag findTag(String key) {
        for (Tag tag : mTags) {
            if (tag.key.equals(key)) {
                return tag;
            }
        }
        return null;
    }

    /** 在已加载的标签里做重名检查，避免在 UI 线程查数据库。 */
    private boolean labelExists(String label, String excludeKey) {
        for (Tag tag : mTags) {
            if (excludeKey != null && excludeKey.equals(tag.key)) {
                continue;
            }
            if (tag.label.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    private static String textOf(TextView view) {
        CharSequence text = view == null ? null : view.getText();
        return text == null ? "" : text.toString();
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }
}
