package com.wtz.videomaker.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.wtz.videomaker.R;
import com.wtz.videomaker.utils.ImageChooser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageChooserGridAdapter extends BaseRecyclerViewAdapter<ImageChooserGridAdapter.Holder> {

    private String mDirPath;
    private List<ImageChooser.ItemData> mDataList = new ArrayList<>();
    private Map<String, Boolean> mDirAllChooseState = new HashMap<>();
    private Map<String, Set<ImageChooser.ItemData>> mDirChooseSetMap = new HashMap<>();
    private LinkedHashSet<ImageChooser.ItemData> mTotalImgChooseSet = new LinkedHashSet<>();

    private int mItemWidth;
    private int mItemHeight;

    public interface OnAllSelectStateChangedListener {
        void onAllSelectStateChanged(boolean isAllSelected);
    }

    private OnAllSelectStateChangedListener mAllSelectStateChangedListener;

    public void setOnAllSelectStateChangedListener(OnAllSelectStateChangedListener listener) {
        this.mAllSelectStateChangedListener = listener;
    }

    public ImageChooserGridAdapter(Context context, String dirPath, List<ImageChooser.ItemData> data,
                                   int itemWidth, int itemHeight) {
        this.mDirPath = dirPath;
        this.mItemWidth = itemWidth;
        this.mItemHeight = itemHeight;
        if (data != null && data.size() > 0) {
            this.mDataList.addAll(data);
        }
    }

    public void update(String dirPath, List<ImageChooser.ItemData> data) {
        this.mDirPath = dirPath;
        mDataList.clear();
        if (data != null && data.size() > 0) {
            this.mDataList.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_chooser, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        lp.height = mItemHeight;//只设置高度，不要设置宽度，宽度直接使用 xml 中的 match_parent 即可
        holder.itemView.setLayoutParams(lp);

        ImageChooser.ItemData item = mDataList.get(position);
        String showName;
        String imagePath;
        if (item.type == ImageChooser.ItemData.TYPE_IMG) {
            showName = item.name;
            imagePath = "file://" + item.path;
        } else {
            showName = item.name + "(" + item.count + "张)";
            imagePath = "file://" + item.cover;
        }
        holder.name.setText(showName);
        Picasso.get()
                .load(imagePath)
                .resize(mItemWidth, mItemHeight)// 解决 OOM 问题
                .centerCrop()// 需要先调用fit或resize设置目标大小，否则会报错：Center crop requires calling resize with positive width and height
                .placeholder(R.drawable.image_default)
                .into(holder.image);

        boolean selected = mTotalImgChooseSet.contains(item);
        setSelectEffect(selected, holder.selectIcon, holder.coverDark);

        bindItemClickListener(holder);
    }

    private void setSelectEffect(boolean selected, ImageView ivSelectIcon, View vCoverDark) {
        if (selected) {
            ivSelectIcon.setVisibility(View.VISIBLE);
            vCoverDark.setVisibility(View.VISIBLE);
        } else {
            ivSelectIcon.setVisibility(View.GONE);
            vCoverDark.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 : mDataList.size();
    }

    public void clickImageItem(View itemView, int position) {
        ImageView ivSelectIcon = itemView.findViewById(R.id.iv_select_icon);
        View vCoverDark = itemView.findViewById(R.id.v_cover_dark);

        ImageChooser.ItemData itemData = mDataList.get(position);
        boolean selected = mTotalImgChooseSet.contains(itemData);
        selected = !selected;
        selectItem(itemData, selected);
        setSelectEffect(selected, ivSelectIcon, vCoverDark);
    }

    /**
     * 这里是外部调用的主动全选行为，不需要全选状态反馈
     * @param isAllSelected true:全选；false:全不选
     */
    public void selectAll(boolean isAllSelected) {
        Set<ImageChooser.ItemData> dirChooseSet = mDirChooseSetMap.get(mDirPath);
        if (dirChooseSet == null) {
            dirChooseSet = new HashSet<>();
            mDirChooseSetMap.put(mDirPath, dirChooseSet);
        }
        if (isAllSelected) {
            dirChooseSet.addAll(mDataList);
            mTotalImgChooseSet.addAll(mDataList);
            mDirAllChooseState.put(mDirPath, true);
        } else {
            dirChooseSet.removeAll(mDataList);
            mTotalImgChooseSet.removeAll(mDataList);
            mDirAllChooseState.put(mDirPath, false);
        }
        notifyDataSetChanged();
    }

    private void selectItem(ImageChooser.ItemData item, boolean isSelected) {
        Set<ImageChooser.ItemData> dirChooseSet = mDirChooseSetMap.get(mDirPath);
        if (dirChooseSet == null) {
            dirChooseSet = new HashSet<>();
            mDirChooseSetMap.put(mDirPath, dirChooseSet);
        }
        if (isSelected) {
            dirChooseSet.add(item);
            mTotalImgChooseSet.add(item);
        } else {
            dirChooseSet.remove(item);
            mTotalImgChooseSet.remove(item);
        }
        checkAllChosenOnSingleItemSelected(dirChooseSet);
    }

    /**
     * 针对单个条目选择发生变化时，检查是否已全选，并进行全选状态反馈
     */
    private void checkAllChosenOnSingleItemSelected(Set<ImageChooser.ItemData> dirChooseSet) {
        Boolean isAllChosen = mDirAllChooseState.get(mDirPath);
        if (dirChooseSet.size() == mDataList.size()) {
            if (isAllChosen == null || isAllChosen.booleanValue() == false) {
                mDirAllChooseState.put(mDirPath, true);
                if (mAllSelectStateChangedListener != null) {
                    mAllSelectStateChangedListener.onAllSelectStateChanged(true);
                }
            }
        } else {
            if (isAllChosen != null && isAllChosen.booleanValue() == true) {
                mDirAllChooseState.put(mDirPath, false);
                if (mAllSelectStateChangedListener != null) {
                    mAllSelectStateChangedListener.onAllSelectStateChanged(false);
                }
            }
        }
    }

    public boolean isAllSelected(String dirpath) {
        Boolean isAllChosen = mDirAllChooseState.get(dirpath);
        return isAllChosen != null && isAllChosen.booleanValue() == true;
    }

    public int getSelectedItemCount() {
        return mTotalImgChooseSet.size();
    }

    public ImageChooser.ItemData[] getSelectedItems() {
        return mTotalImgChooseSet.toArray(new ImageChooser.ItemData[0]);
    }

    public class Holder extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView name;
        private ImageView selectIcon;
        private View coverDark;

        public Holder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.tv_name);
            image = (ImageView) itemView.findViewById(R.id.iv_icon);
            selectIcon = itemView.findViewById(R.id.iv_select_icon);
            coverDark = itemView.findViewById(R.id.v_cover_dark);
        }
    }

}
