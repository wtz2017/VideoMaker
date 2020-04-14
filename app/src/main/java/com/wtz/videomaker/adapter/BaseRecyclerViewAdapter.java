package com.wtz.videomaker.adapter;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public abstract class BaseRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    public interface OnRecyclerViewItemClickListener {

        void onItemClick(View view, int position);

        boolean onItemLongClick(View view, int position);

    }

    protected OnRecyclerViewItemClickListener mOnItemClickListener;

    public void setOnItemClickListener(OnRecyclerViewItemClickListener listener) {
        this.mOnItemClickListener = listener;
    }

    protected void bindItemClickListener(@NonNull final RecyclerView.ViewHolder holder) {
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mOnItemClickListener != null) {
                    int pos = holder.getLayoutPosition();
                    mOnItemClickListener.onItemClick(holder.itemView, pos);
                }
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mOnItemClickListener != null) {
                    int pos = holder.getLayoutPosition();
                    return mOnItemClickListener.onItemLongClick(holder.itemView, pos);
                }
                return false;
            }
        });
    }

}
