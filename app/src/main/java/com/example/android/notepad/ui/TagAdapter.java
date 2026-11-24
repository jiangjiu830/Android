package com.example.android.notepad.ui;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.notepad.R;
import com.example.android.notepad.data.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * 标签管理列表的适配器：显示颜色圆点、名称，并提供「重命名 / 删除」入口。
 */
public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagHolder> {

    public interface Listener {
        void onRename(Tag tag);

        void onDelete(Tag tag);
    }

    private final Listener mListener;
    private final List<Tag> mTags = new ArrayList<>();

    public TagAdapter(Listener listener) {
        mListener = listener;
    }

    /** 整体替换标签列表。 */
    public void submit(List<Tag> tags) {
        mTags.clear();
        if (tags != null) {
            mTags.addAll(tags);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TagHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag, parent, false);
        return new TagHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagHolder holder, int position) {
        holder.bind(mTags.get(position));
    }

    @Override
    public int getItemCount() {
        return mTags.size();
    }

    class TagHolder extends RecyclerView.ViewHolder {

        private final View mDot;
        private final TextView mLabel;

        TagHolder(@NonNull View itemView) {
            super(itemView);
            mDot = itemView.findViewById(R.id.tag_dot);
            mLabel = itemView.findViewById(R.id.tag_label);

            itemView.findViewById(R.id.tag_rename).setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onRename(mTags.get(position));
                }
            });
            itemView.findViewById(R.id.tag_delete).setOnClickListener(view -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && mListener != null) {
                    mListener.onDelete(mTags.get(position));
                }
            });
        }

        void bind(Tag tag) {
            mLabel.setText(tag.label);
            mDot.setBackgroundTintList(ColorStateList.valueOf(tag.color));
        }
    }
}
