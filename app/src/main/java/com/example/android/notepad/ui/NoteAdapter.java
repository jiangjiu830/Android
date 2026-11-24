package com.example.android.notepad.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.util.Prefs;
import com.example.android.notepad.util.SearchText;
import com.example.android.notepad.util.TimeUtils;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记列表适配器。
 * <p>
 * 负责：显示标题 / 正文摘要 / 标签 / 置顶标记 / 修改时间戳，
 * 并在搜索时高亮命中的关键字。
 * <p>
 * 说明：这里直接继承 {@link RecyclerView.Adapter}，用 {@link #submit(List)}
 * 同步替换数据并刷新，逻辑简单、行为可预期；笔记数量不多，无需 DiffUtil 增量更新。
 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteHolder> {

    /** 列表项交互回调 */
    public interface Listener {
        void onNoteClick(Note note);

        void onNoteLongClick(Note note);
    }

    private final Context mContext;
    private final Listener mListener;
    private final List<Note> mNotes = new ArrayList<>();

    private final int mAccentColor;
    private final int mPinTextColor;
    private final int mPinBackgroundColor;

    private String mKeyword = "";
    private boolean mShowPreview;
    private int mPreviewTextSize;

    public NoteAdapter(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
        mShowPreview = Prefs.showPreview(context);
        mPreviewTextSize = Prefs.fontSize(context);

        mAccentColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, 0xFF3A6B57);
        mPinTextColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF002014);
        mPinBackgroundColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimaryContainer, 0xFFBCEBD4);
    }

    /** 整体替换数据；笔记量小，用全量刷新换取稳定（原因见类注释）。 */
    public void submit(List<Note> notes) {
        mNotes.clear();
        if (notes != null) {
            mNotes.addAll(notes);
        }
        notifyDataSetChanged();
    }

    /** 供滑动删除按位置取笔记；越界返回 null。 */
    public Note noteAt(int position) {
        if (position < 0 || position >= mNotes.size()) {
            return null;
        }
        return mNotes.get(position);
    }

    /** 关键字变化时重绘，让高亮与当前搜索保持一致。 */
    public void setKeyword(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.equals(mKeyword)) {
            return;
        }
        mKeyword = normalized;
        notifyDataSetChanged();
    }

    /** 是否显示正文摘要（设置项）。 */
    public void setShowPreview(boolean show) {
        if (mShowPreview == show) {
            return;
        }
        mShowPreview = show;
        notifyDataSetChanged();
    }

    /** 摘要字号（设置项），只影响列表预览。 */
    public void setPreviewTextSize(int sizeSp) {
        if (mPreviewTextSize == sizeSp) {
            return;
        }
        mPreviewTextSize = sizeSp;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mNotes.size();
    }

    @NonNull
    @Override
    public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
        holder.bind(mNotes.get(position));
    }

    class NoteHolder extends RecyclerView.ViewHolder {

        private final TextView mTitle;
        private final TextView mPreview;
        private final TextView mTime;
        private final TextView mCategory;
        private final TextView mPin;

        NoteHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.text_title);
            mPreview = itemView.findViewById(R.id.text_preview);
            mTime = itemView.findViewById(R.id.text_time);
            mCategory = itemView.findViewById(R.id.badge_category);
            mPin = itemView.findViewById(R.id.badge_pin);

            itemView.setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onNoteClick(noteAt(position));
                }
            });
            itemView.setOnLongClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onNoteLongClick(noteAt(position));
                    return true;
                }
                return false;
            });
        }

        /** 绑定一条笔记：标题/摘要高亮、分类颜色、置顶标记与时间戳。 */
        void bind(Note note) {
            mTitle.setText(SearchText.highlight(
                    note.displayTitle(mContext), mKeyword, mAccentColor));

            // 正文摘要：设置里可以关闭；搜索时把命中位置提到前面
            String preview = mShowPreview
                    ? SearchText.snippet(note.content, mKeyword, 100)
                    : "";
            if (preview.isEmpty()) {
                mPreview.setVisibility(View.GONE);
            } else {
                mPreview.setVisibility(View.VISIBLE);
                mPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, mPreviewTextSize);
                mPreview.setText(SearchText.highlight(preview, mKeyword, mAccentColor));
            }

            int tagColor = note.tagColor != 0
                    ? note.tagColor
                    : mContext.getColor(R.color.category_default);
            String tagLabel = TextUtils.isEmpty(note.tagLabel) ? note.category : note.tagLabel;
            mCategory.setText(tagLabel);
            Pills.tint(mCategory, tagColor, tagColor);

            // 置顶标记
            mPin.setVisibility(note.pinned ? View.VISIBLE : View.GONE);
            if (note.pinned) {
                Pills.tint(mPin, mPinTextColor, mPinBackgroundColor);
            }

            // 时间戳：今天 09:30 / 昨天 18:05 / 3月5日 / 2025年3月5日
            mTime.setText(TimeUtils.formatListTime(note.modified));
        }
    }
}
