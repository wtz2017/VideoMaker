package com.wtz.videomaker.utils;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.wtz.libvideomaker.utils.LogUtils;
import com.wtz.libvideomaker.utils.ScreenUtils;
import com.wtz.videomaker.R;
import com.wtz.videomaker.adapter.BaseRecyclerViewAdapter;
import com.wtz.videomaker.adapter.ImageChooserGridAdapter;
import com.wtz.videomaker.views.GridItemDecoration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ImageChooser extends AppCompatActivity implements View.OnClickListener,
        ImageChooserGridAdapter.OnAllSelectStateChangedListener {
    private static final String TAG = ImageChooser.class.getSimpleName();

    public static final String KEY_IMAGE_LIST = "image_list";

    private RecyclerView mImageGridView;
    private TextView mChooseTipsTV;
    private CheckBox mChooseAllCB;
    private Button mBackButton;
    private Button mConfirmButton;

    private static final int CHOOSE_ALL_CHECKED_TYPE_ACTION = 0;
    private static final int CHOOSE_ALL_CHECKED_TYPE_STATE = 1;
    private int mChooseAllCheckedType;

    private HashMap<String, List<ItemData>> mAllImages = new HashMap<>();//所有照片
    private List<ItemData> mDirs = new ArrayList<>();
    private List<ItemData> mCurrentList;
    private String mCurrentDirPath = "";

    private ImageChooserGridAdapter mImageChooserGridAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        LogUtils.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_image_chooser);
        mImageGridView = findViewById(R.id.recycler_view_images);
        mChooseTipsTV = findViewById(R.id.tv_choose_tips);
        mChooseAllCB = findViewById(R.id.cb_choose_all);
        mBackButton = findViewById(R.id.btn_back);
        mConfirmButton = findViewById(R.id.btn_confirm);

        initImageGridView();
        mBackButton.setOnClickListener(this);
        mConfirmButton.setOnClickListener(this);
        mChooseAllCB.setOnTouchListener(mChooseAllCBTouchListener);
        mChooseAllCB.setOnCheckedChangeListener(OnChooseAllCheckedChangeListener);

        new Thread(new Runnable() {
            @Override
            public void run() {
                listImages(ImageChooser.this);
            }
        }).start();
    }

    private void initImageGridView() {
        int[] wh = ScreenUtils.getScreenPixels(this);
        int divideWidth = (int) getResources().getDimension(R.dimen.dp_170);
        int paddingX = (int) getResources().getDimension(R.dimen.dp_4);
        int spanCount = wh[0] / divideWidth;
        int itemWidth = wh[0] / spanCount - paddingX;
        int itemHeight = (int) (1.3f * itemWidth);

        ViewGroup.LayoutParams lp = mImageGridView.getLayoutParams();
        lp.width = itemWidth * spanCount;
        mImageGridView.setLayoutParams(lp);

        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        mImageGridView.setLayoutManager(layoutManager);

        mImageGridView.addItemDecoration(
                new GridItemDecoration(this, R.drawable.grid_divider_line_shape, true));

        mImageChooserGridAdapter = new ImageChooserGridAdapter(this, mCurrentDirPath, mCurrentList, itemWidth, itemHeight);
        mImageChooserGridAdapter.setOnAllSelectStateChangedListener(this);
        mImageChooserGridAdapter.setOnItemClickListener(new BaseRecyclerViewAdapter.OnRecyclerViewItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                LogUtils.d(TAG, "mChannelsAdapter onItemClick position=" + position);
                ItemData item = mCurrentList.get(position);
                if (item.type == ItemData.TYPE_DIR) {
                    mChooseTipsTV.setText("选择图片");
                    mChooseAllCB.setVisibility(View.VISIBLE);
                    mCurrentDirPath = item.path;
                    mCurrentList = mAllImages.get(mCurrentDirPath);
                    mImageChooserGridAdapter.update(mCurrentDirPath, mCurrentList);
                    syncAllCheckState();
                } else {
                    mImageChooserGridAdapter.clickImageItem(view, position);
                    checkSelecedCount();
                }
            }

            @Override
            public boolean onItemLongClick(View view, int position) {
                LogUtils.d(TAG, "mChannelsAdapter onItemLongClick position=" + position);
                return true;
            }
        });
        mImageGridView.setAdapter(mImageChooserGridAdapter);
    }

    private void syncAllCheckState() {
        mChooseAllCheckedType = CHOOSE_ALL_CHECKED_TYPE_STATE;
        if (mImageChooserGridAdapter.isAllSelected(mCurrentDirPath)) {
            if (!mChooseAllCB.isChecked()) {
                mChooseAllCB.setChecked(true);
            }
        } else {
            if (mChooseAllCB.isChecked()) {
                mChooseAllCB.setChecked(false);
            }
        }
    }

    private void checkSelecedCount() {
        int count = mImageChooserGridAdapter.getSelectedItemCount();
        if (count > 0) {
            mConfirmButton.setText("确认(" + mImageChooserGridAdapter.getSelectedItemCount() + ")");
            mConfirmButton.setVisibility(View.VISIBLE);
        } else {
            mConfirmButton.setVisibility(View.INVISIBLE);
        }
    }

    private void listImages(Context context) {
        mAllImages.clear();
        mDirs.clear();
        Uri mImageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projImage = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME};
        final Cursor mCursor = context.getContentResolver().query(mImageUri,
                projImage,
                MediaStore.Images.Media.MIME_TYPE + "=? or " + MediaStore.Images.Media.MIME_TYPE + "=?",
                new String[]{"image/jpeg", "image/png"},
                MediaStore.Images.Media.DATE_MODIFIED + " desc");

        if (mCursor != null) {
            while (mCursor.moveToNext()) {
                // 获取图片的路径
                String path = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.Media.DATA));
                String displayName = mCursor.getString(mCursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
                String dirPath = new File(path).getParentFile().getAbsolutePath();
                // 区分目录
                if (mAllImages.containsKey(dirPath)) {
                    List<ItemData> dataList = mAllImages.get(dirPath);
                    dataList.add(new ItemData(ItemData.TYPE_IMG, path, displayName));
                } else {
                    List<ItemData> dataList = new ArrayList<>();
                    dataList.add(new ItemData(ItemData.TYPE_IMG, path, displayName));
                    mAllImages.put(dirPath, dataList);

                    File dir = new File(dirPath);
                    mDirs.add(new ItemData(ItemData.TYPE_DIR, dirPath, dir.getName(), path));
                }
            }
            for (ItemData dir : mDirs) {
                List<ItemData> dataList = mAllImages.get(dir.path);
                dir.count = dataList.size();
            }
            mCursor.close();
        }

        mCurrentList = mDirs;
        runOnUiThread(mUpdateImagesRunnable);
    }

    private Runnable mUpdateImagesRunnable = new Runnable() {
        @Override
        public void run() {
            if (mImageChooserGridAdapter == null) return;
            mImageChooserGridAdapter.update(mCurrentDirPath, mCurrentList);
        }
    };

    private View.OnTouchListener mChooseAllCBTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mChooseAllCheckedType = CHOOSE_ALL_CHECKED_TYPE_ACTION;
            }
            return false;
        }
    };

    @Override
    public void onAllSelectStateChanged(boolean isAllSelected) {
        LogUtils.d(TAG, "onAllSelectStateChanged isAllSelected=" + isAllSelected);
        mChooseAllCheckedType = CHOOSE_ALL_CHECKED_TYPE_STATE;
        if (isAllSelected) {
            mChooseAllCB.setChecked(true);
        } else {
            mChooseAllCB.setChecked(false);
        }
    }

    private CompoundButton.OnCheckedChangeListener OnChooseAllCheckedChangeListener =
            new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            LogUtils.d(TAG, "mChooseAllCB onCheckedChanged: " + isChecked
                    + " CheckedType:" + mChooseAllCheckedType);
            if (mChooseAllCheckedType != CHOOSE_ALL_CHECKED_TYPE_ACTION) {
                return;
            }
            mImageChooserGridAdapter.selectAll(isChecked);
            checkSelecedCount();
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtils.d(TAG, "onKeyDown keyCode=" + keyCode + " action=" + event.getAction());
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        LogUtils.d(TAG, "onKeyUp keyCode=" + keyCode + " action=" + event.getAction());
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mCurrentList != null && mCurrentList.get(0).type == ItemData.TYPE_IMG) {
                    backToDirs();
                    return true;
                } else {
                    cancelChoose();
                }
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onClick(View v) {
        LogUtils.d(TAG, "onClick " + v);
        switch (v.getId()) {
            case R.id.btn_back:
                if (mCurrentList != null && mCurrentList.get(0).type == ItemData.TYPE_IMG) {
                    backToDirs();
                } else {
                    cancelChoose();
                }
                break;

            case R.id.btn_confirm:
                confirmChoose();
                break;
        }
    }

    private void backToDirs() {
        mCurrentDirPath = "";
        mCurrentList = mDirs;
        mChooseTipsTV.setText("选择相册");
        mChooseAllCB.setVisibility(View.INVISIBLE);
        mImageChooserGridAdapter.update(mCurrentDirPath, mCurrentList);
    }

    private void cancelChoose() {
        setResult(RESULT_CANCELED);
        finish();
    }

    private void confirmChoose() {
        Intent intent = new Intent();
        ImageChooser.ItemData[] list = mImageChooserGridAdapter.getSelectedItems();
        ArrayList<String> resultList = new ArrayList<>();
        for (ImageChooser.ItemData itemData : list) {
            resultList.add(itemData.path);
        }
        intent.putStringArrayListExtra(KEY_IMAGE_LIST, resultList);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        LogUtils.d(TAG, "onDestroy");
        mImageChooserGridAdapter = null;
        super.onDestroy();
    }

    public static class ItemData {
        public static final int TYPE_DIR = 0;
        public static final int TYPE_IMG = 1;
        public int type;
        public String path;
        public String name;

        // for folder
        public String cover;// folder cover path
        public int count = 0;// image count in folder

        public ItemData(int type, String path, String name) {
            this.type = type;
            this.path = path;
            this.name = name;
        }

        public ItemData(int type, String path, String name, String cover) {
            this.type = type;
            this.path = path;
            this.name = name;
            this.cover = cover;
        }
    }

}
