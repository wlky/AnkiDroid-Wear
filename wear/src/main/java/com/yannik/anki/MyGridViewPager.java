package com.yannik.anki;

import android.content.Context;
import android.graphics.Point;
import android.support.wearable.view.GridViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * @author Created by Yannik on 05.01.2016.
 */
public class MyGridViewPager extends GridViewPager {
    private float mDownY;
    private float SCROLL_THRESHOLD=0;
    private boolean dontBlockYMovement = false;

    public MyGridViewPager(Context context) {
        super(context);
        init();
    }

    public MyGridViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MyGridViewPager(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private void init(){
        SCROLL_THRESHOLD = ViewConfiguration.get(getContext())
                .getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {


        if (getAdapter() != null) {
            Point curItem = getCurrentItem();
            int numRows = getAdapter().getRowCount();
            if (numRows <= 1) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mDownY = event.getY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    if (dontBlockYMovement || Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD) {
                        dontBlockYMovement = true;
                        //                        Log.d(getClass().getName(), "Not blocking Y
                        // movement. FirstDownY: " +
                        //                                mDownY + " new Y " + event.getY());
                        return false;
                    }
                } else {
                    dontBlockYMovement = false;
                }
            }
        }
        dontBlockYMovement = false;
        //        Log.d(getClass().getName(), "Blocking Y movement");
        return super.onInterceptTouchEvent(event);
    }
}
