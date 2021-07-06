package com.yannik.anki;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by Yannik on 06.02.2015.
 */
public class PullButton extends RelativeLayout {

    private final ImageButton icon;
    private final TextView textView;
    private final TextView easeTextView;
    OnClickListener ocl;
    private float homePosition, extendedPosition, homeAlpha = 0.7f, extendedAlpha = 1;
    private int minMovementDistance = 50;
    private Point displaySize = new Point();
    private int exitY = 0;
    private int imageResId = -1;
    private boolean upsideDown;

    public PullButton(Context context) {
        this(context, null);
    }

    public PullButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PullButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PullButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.pull_button, this);

        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        display.getSize(displaySize);


        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PullButton);
        final int N = a.getIndexCount();

        icon = (ImageButton) findViewById(R.id.icon);
        textView = (TextView) findViewById(R.id.textView);
        easeTextView = (TextView) findViewById(R.id.ease_text);



        for (int i = 0; i < N; ++i) {
            int attr = a.getIndex(i);
            switch (attr) {
                case R.styleable.PullButton_icon:
                    imageResId = a.getResourceId(attr, R.drawable.close_button);
                    break;
                case R.styleable.PullButton_text:
                    String text = a.getString(attr);
                    textView.setText(text);
                    break;
                case R.styleable.PullButton_upsideDown:
                    upsideDown = a.getBoolean(attr, false);

                    break;
                case R.styleable.PullButton_ease_text:
                    easeTextView.setText(a.getString(attr));
                    break;
            }
        }
        a.recycle();



        ViewTreeObserver vto = getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (upsideDown) {
                    textView.setY(0);
                    icon.setY(textView.getHeight());
                    icon.setRotation(180);
                    easeTextView.setY(easeTextView.getY() + textView.getHeight());
                }


                if (upsideDown) {
                    homePosition = 0 - textView.getHeight();
                    extendedPosition = 0;
                    exitY = displaySize.y + 10;
                } else {
                    homePosition = displaySize.y - icon.getHeight();
                    extendedPosition = displaySize.y - getHeight();
                    exitY = -getHeight() - 10;
                }

                setY(homePosition);

                ViewTreeObserver obs = getViewTreeObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    obs.removeOnGlobalLayoutListener(this);
                } else {
                    obs.removeGlobalOnLayoutListener(this);
                }
            }
        });


        if (imageResId != -1) {
            icon.setImageResource(imageResId);
        }



        minMovementDistance = displaySize.y / 2;


        setAlpha(homeAlpha);


        this.animate().setInterpolator(new LinearInterpolator());
        this.setOnTouchListener(new SwipeTouchListener());

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return true;
    }

    public void right() {
        setX(displaySize.x / 2);
    }

    public void left() {
        setX(displaySize.x / 2 - getWidth());
    }

    public void centerX() {
        setX(displaySize.x / 2 - getWidth() / 2);
    }

    public void setOnSwipeListener(OnClickListener ocl) {
        this.ocl = ocl;
    }

    public void setImageRessource(int res) {
        imageResId = res;
        if (icon != null) {
            icon.setImageResource(res);
        }
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void slideIn(long delay) {
        if (upsideDown) {
            setY(-getHeight());
        } else {
            setY(displaySize.y);
        }
        setAlpha(homeAlpha);
        setVisibility(View.VISIBLE);
        animate().setStartDelay(delay).y(homePosition).setListener(null);
    }

    public void animateOut(float velocity) {

        animate()
                .setStartDelay(0)
                .y(exitY)
                .setDuration(Math.min((long) ((Math.abs(getY() - exitY)) / Math.abs(velocity)), 500))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (ocl != null) ocl.onClick(PullButton.this);
                        setY(displaySize.y + 10);
                        animate().setStartDelay(0).y(homePosition).setListener(null).setDuration(250);
                    }
                });
    }

    class SwipeTouchListener implements OnTouchListener {
        private float yDiff;

        private VelocityTracker mVelocityTracker;

        @Override
        public boolean onTouch(final View v, MotionEvent event) {
/*            if(exitY == Integer.MAX_VALUE){
                exitY = -v.getHeight() - 10;
            }*/
            float viewPositionY = v.getY();
            float eventPositionY = event.getRawY();
            if (upsideDown) {
                viewPositionY = displaySize.y - viewPositionY - v.getHeight();
                eventPositionY = displaySize.y - eventPositionY;
            }


            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDiff = eventPositionY - viewPositionY;

                    mVelocityTracker = VelocityTracker.obtain();
                    event.offsetLocation(0, homePosition - viewPositionY);
                    mVelocityTracker.addMovement(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!upsideDown) {
                        v.setY(eventPositionY - yDiff);
                    } else {
                        v.setY(displaySize.y - (eventPositionY - yDiff) - v.getHeight());
                    }
                    event.offsetLocation(0, homePosition - viewPositionY);
                    mVelocityTracker.addMovement(event);

                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:

                    mVelocityTracker.computeCurrentVelocity(1);
                    float yVelocity = mVelocityTracker.getYVelocity();

                    if (viewPositionY < minMovementDistance && ((upsideDown && yVelocity >= 0) || (!upsideDown && yVelocity <= 0))) {

                        System.out.println("Velocity is: " + yVelocity);
                        System.out.println("Distance is: " + (viewPositionY - exitY));
                        animateOut(mVelocityTracker.getYVelocity());
                    } else if (viewPositionY + v.getHeight() < displaySize.y) {
                        v.animate().setStartDelay(0).y(extendedPosition).alpha(extendedAlpha).setListener(null);
                    } else {
                        v.animate().setStartDelay(0).y(homePosition).alpha(homeAlpha).setListener(null);
                    }

                    mVelocityTracker.recycle();
                    break;
            }


            return true;
        }
    }

}
