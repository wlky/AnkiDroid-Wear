package com.yannik.anki;

import android.content.Context;
import android.support.wearable.view.GridViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Created by Yannik on 05.01.2016.
 */
public class MyGridViewPager extends GridViewPager {
    private float mDownY;
    private float SCROLL_THRESHOLD=0;

    public MyGridViewPager(Context context) {
        super(context);
        init();
    }

    public MyGridViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init(){
        SCROLL_THRESHOLD = ViewConfiguration.get(getContext())
                .getScaledTouchSlop();
    }

    private boolean blockYMovement = false;
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {

        if (getAdapter() != null && getAdapter().getColumnCount(getCurrentItem().x) > 1) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mDownY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (blockYMovement || Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD) {
                    blockYMovement = true;
                    return false;
                }
            } else {
                blockYMovement = false;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    public MyGridViewPager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
