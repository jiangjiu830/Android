package com.example.android.notepad.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Note;
import com.example.android.notepad.util.TimeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 回收站列表适配器：显示笔记标题、删除时间，并提供「恢复 / 彻底删除」按钮。
 */
public class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.TrashHolder> {

    public interface Listener {
        void onRestore(Note note);

        void onDeleteForever(Note note);
    }

    private final Context mContext;
    private final Listener mListener;
    private final List<Note> mNotes = new ArrayList<>();

    public TrashAdapter(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    /** 整体替换回收站列表。 */
    public void submit(List<Note> notes) {
        mNotes.clear();
        if (notes != null) {
            mNotes.addAll(notes);
        }
        notifyDataSetChanged();
    }

    /** 按位置取笔记；越界返回 null。 */
    public Note noteAt(int position) {
        if (position < 0 || position >= mNotes.size()) {
            return null;
        }
        return mNotes.get(position);
    }

    @Override
    public int getItemCount() {
        return mNotes.size();
    }

    @NonNull
    @Override
    public TrashHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trash, parent, false);
        return new TrashHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrashHolder holder, int position) {
        holder.bind(mNotes.get(position));
    }

    class TrashHolder extends RecyclerView.ViewHolder {

        private final TextView mTitle;
        private final TextView mTime;

        TrashHolder(@NonNull View itemView) {
            super(itemView);
            mTitle = itemView.findViewById(R.id.text_title);
            mTime = itemView.findViewById(R.id.text_time);
            itemView.findViewById(R.id.button_restore).setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onRestore(noteAt(position));
                }
            });
            itemView.findViewById(R.id.button_delete).setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onDeleteForever(noteAt(position));
                }
            });
        }

        /** 显示标题与删除时间；老数据没有 deleted_at 时退回修改时间。 */
        void bind(Note note) {
            mTitle.setText(note.displayTitle(mContext));
            long deletedAt = note.deletedAt > 0 ? note.deletedAt : note.modified;
            mTime.setText(mContext.getString(R.string.deleted_at,
                    TimeUtils.formatDateTime(deletedAt)));
        }
    }
}
