package com.wtz.videomaker.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.HashSet;
import java.util.Set;

public class GridItemDecoration extends RecyclerView.ItemDecoration {
    private static final String TAG = "GridItemDecoration";

    private Drawable mDivider;
    private boolean keepBorderLine;

    // 默认：会在每个 item 的下边和右边留出分割线的位置
    private Set<Integer> mNeedDrawTopLineSet = new HashSet<>();// 需要在 item 的上边画线的位置集合
    private Set<Integer> mNeedDrawLeftLineSet = new HashSet<>();// 需要在 item 的左边画线的位置集合

    // 使用系统的一个属性 android.R.attrs.listDriver 也可以
    public GridItemDecoration(Context context, @DrawableRes int drawableResId, boolean keepBorderLine) {
        mDivider = ContextCompat.getDrawable(context, drawableResId);
        this.keepBorderLine = keepBorderLine;
    }

    /**
     * 针对每个 item 都会调用一次
     */
    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        // 默认：在每个 item 的下边和右边留出分割线的位置
        int spanCount = getSpanCount(parent);
        int childCount = parent.getAdapter().getItemCount();
        int childPosition = parent.getChildAdapterPosition(view);

        mNeedDrawTopLineSet.remove(childPosition);
        mNeedDrawLeftLineSet.remove(childPosition);
        if (keepBorderLine) {
            /* 要最外边分割线的做法 */
            if (childPosition == 0) {
                // 既是第一行，又是第一列，需要绘制顶部和左边
                mNeedDrawTopLineSet.add(childPosition);
                mNeedDrawLeftLineSet.add(childPosition);
                outRect.set(mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight(), mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            } else if (isFirstRow(parent, childPosition, spanCount, childCount)) {
                // 如果是第一行，还需要绘制顶部
                mNeedDrawTopLineSet.add(childPosition);
                outRect.set(0, mDivider.getIntrinsicHeight(), mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            } else if (isFirstColumn(parent, childPosition, spanCount, childCount)) {
                // 如果是第一列，还需要绘制左边
                mNeedDrawLeftLineSet.add(childPosition);
                outRect.set(mDivider.getIntrinsicWidth(), 0, mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            } else {
                outRect.set(0, 0, mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            }
        } else {
            /* 不要最外边分割线的做法 */
            if (childPosition == childCount - 1) {
                // 既是最后一行，又是最后一列，不需要绘制底部和右边
                outRect.set(0, 0, 0, 0);
            } else if (isLastRow(parent, childPosition, spanCount, childCount)) {
                // 如果是最后一行，则不需要绘制底部
                outRect.set(0, 0, mDivider.getIntrinsicWidth(), 0);
            } else if (isLastColumn(parent, childPosition, spanCount, childCount)) {
                // 如果是最后一列，则不需要绘制右边
                outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
            } else {
                outRect.set(0, 0, mDivider.getIntrinsicWidth(), mDivider.getIntrinsicHeight());
            }
        }
    }

    /**
     * 针对总体的 item 调用一次
     */
    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        drawLeftLine(c, parent);
        drawTopLine(c, parent);
        drawBottomLine(c, parent);
        drawRightLine(c, parent);
    }

    /**
     * 在 item 左边绘制垂直方向的分割线
     */
    private void drawLeftLine(Canvas c, RecyclerView parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (mNeedDrawLeftLineSet.contains(i)) {
                View childView = parent.getChildAt(i);
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) childView.getLayoutParams();

                // top 减去这个 mDivider.getIntrinsicHeight() 是避免横纵交汇处漏点
                int top = childView.getTop() - params.topMargin- mDivider.getIntrinsicHeight();
                // bottom 加上这个 mDivider.getIntrinsicHeight() 是避免横纵交汇处漏点
                int bottom = childView.getBottom() + params.bottomMargin + mDivider.getIntrinsicHeight();
                int right = childView.getLeft() - params.leftMargin;
                int left = right - mDivider.getIntrinsicWidth();

                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }

    /**
     * 在 item 右边绘制垂直方向的分割线
     */
    private void drawRightLine(Canvas c, RecyclerView parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = parent.getChildAt(i);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) childView.getLayoutParams();

            // top 减去这个 mDivider.getIntrinsicHeight() 是避免横纵交汇处漏点
            int top = childView.getTop() - params.topMargin- mDivider.getIntrinsicHeight();
            int bottom = childView.getBottom() + params.bottomMargin;
            int left = childView.getRight() + params.rightMargin;
            int right = left + mDivider.getIntrinsicWidth();

            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(c);
        }
    }

    /**
     * 在 item 顶边绘制水平方向的分割线
     */
    private void drawTopLine(Canvas c, RecyclerView parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (mNeedDrawTopLineSet.contains(i)) {
                View childView = parent.getChildAt(i);
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) childView.getLayoutParams();

                // left 减去这个 mDivider.getIntrinsicWidth() 是避免横纵交汇处漏点
                int left = childView.getLeft() - params.leftMargin - mDivider.getIntrinsicWidth();
                // right 加上这个 mDivider.getIntrinsicWidth() 是避免横纵交汇处漏点
                int right = childView.getRight() + mDivider.getIntrinsicWidth() + params.rightMargin;
                int bottom = childView.getTop() - params.topMargin;
                int top = bottom - mDivider.getIntrinsicHeight();

                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(c);
            }
        }
    }

    /**
     * 在 item 底边绘制水平方向的分割线
     */
    private void drawBottomLine(Canvas c, RecyclerView parent) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = parent.getChildAt(i);
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) childView.getLayoutParams();

            int left = childView.getLeft() - params.leftMargin;
            // right 加上这个 mDivider.getIntrinsicWidth() 是避免横纵交汇处漏点
            int right = childView.getRight() + mDivider.getIntrinsicWidth() + params.rightMargin;
            int top = childView.getBottom() + params.bottomMargin;
            int bottom = top + mDivider.getIntrinsicHeight();

            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(c);
        }
    }

    /**
     * 获取 SpanCount 垂直布局列数，水平布局行数
     */
    private int getSpanCount(RecyclerView parent) {
        int spanCount = -1;
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            spanCount = ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }
        return spanCount;
    }

    private boolean isFirstColumn(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            int orientation = ((GridLayoutManager) layoutManager).getOrientation();
            if (orientation == GridLayoutManager.VERTICAL) {
                // 纵向
                // 0  1  2
                // 3  4  5
                // 6  7
                if (pos % spanCount == 0) {
                    return true;
                }
            } else {
                // 横向
                // 0  3  6
                // 1  4  7
                // 2  5
                if (pos < spanCount) {
                    return true;
                }
            }
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
            if (orientation == StaggeredGridLayoutManager.VERTICAL) {
                if (pos % spanCount == 0) {
                    return true;
                }
            } else {
                if (pos < spanCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLastColumn(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            int orientation = ((GridLayoutManager) layoutManager).getOrientation();
            if (orientation == GridLayoutManager.VERTICAL) {
                if ((pos + 1) % spanCount == 0) {
                    return true;
                }
            } else {
                childCount = childCount - childCount % spanCount;
                if (pos >= childCount) {
                    return true;
                }
            }
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
            if (orientation == StaggeredGridLayoutManager.VERTICAL) {
                if ((pos + 1) % spanCount == 0) {
                    return true;
                }
            } else {
                childCount = childCount - childCount % spanCount;
                if (pos >= childCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFirstRow(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            int orientation = ((GridLayoutManager) layoutManager).getOrientation();
            if (orientation == GridLayoutManager.VERTICAL) {
                // 纵向
                // 0  1  2
                // 3  4  5
                // 6  7
                if (pos < spanCount) {
                    return true;
                }
            } else {
                // 横向
                // 0  3  6
                // 1  4  7
                // 2  5
                if (pos % spanCount == 0) {
                    return true;
                }
            }
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
            if (orientation == StaggeredGridLayoutManager.VERTICAL) {
                if (pos < spanCount) {
                    return true;
                }
            } else {
                if (pos % spanCount == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isLastRow(RecyclerView parent, int pos, int spanCount, int childCount) {
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager) {
            int orientation = ((GridLayoutManager) layoutManager).getOrientation();
            if (orientation == GridLayoutManager.VERTICAL) {
                childCount = childCount - childCount % spanCount;
                if (pos >= childCount) {
                    return true;
                }
            } else {
                if ((pos + 1) % spanCount == 0) {
                    return true;
                }
            }
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            int orientation = ((StaggeredGridLayoutManager) layoutManager).getOrientation();
            if (orientation == StaggeredGridLayoutManager.VERTICAL) {
                childCount = childCount - childCount % spanCount;
                if (pos >= childCount) {
                    return true;
                }
            } else {
                if ((pos + 1) % spanCount == 0) {
                    return true;
                }
            }
        }
        return false;
    }

}
