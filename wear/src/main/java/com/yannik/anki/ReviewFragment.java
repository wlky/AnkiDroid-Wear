package com.yannik.anki;

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yannik.sharedvalues.CommonIdentifiers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ReviewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ReviewFragment extends Fragment implements WearMainActivity.JsonReceiver, WearMainActivity.AmbientStatusReceiver {
    private static final String TAG = "ReviewFragment";
    public static final String W2W_RELOAD_HTML_FOR_MEDIA = "reload_text";
    private static final String W2W_REMOVE_SCREEN_LOCK = "remove_screen_lock";
    private static final String SOUND_PLACEHOLDER_STRING = "awlieurablsdkvbwlaiueaghlsdkvblqi2345235.jpg";
    private static final String SOUND_TAG_REPLACEMENT_REGEX = "\\[(sound:[^\\]]+)\\]";
    //    private static final String SOUND_TAG_REPLACEMENT_STRING = "&#128266;";
    private static final String SOUND_TAG_REPLACEMENT_STRING = "<img src='" + SOUND_PLACEHOLDER_STRING +
            "'/>";
    /**
     * Group 1 = Contents of [sound:] tag <br>
     * Group 2 = "fname"
     */
    private static final Pattern fSoundRegexps = Pattern.compile("(?i)(\\[sound:([^]]+)\\])");
    private static final int EASY = 0, MID = 1, HARD = 2, FAILED = 3;
    private static final int GESTURE_BUTTON_ANIMATION_TIME_MS = 1000;
    private static Preferences settings;
    private static GridViewPager gridViewPager;
    byte playSounds = -1;
    MyImageGetter imageGetter = new MyImageGetter();
    private TextView mTextView;
    private long noteID;
    private int cardOrd;
    private RelativeLayout qaOverlay;
    //    private PullButton easeButtons[FAILED], easeButtons[HARD], easeButtons[MID], easeButtons[EASY];
    private PullButton[] easeButtons;
    private boolean showingEaseButtons = false, showingAnswer = false;
    private Timer easeButtonShowTimer = new Timer();
    private ScrollView qaScrollView;
    private ProgressBar spinner;
    private boolean scrollViewMoved;
    private String qHtml;
    private String aHtml;
    private View rotationTarget;
    private long duration = 180;
    private Context mContext;
    private RelativeLayout qaContainer;
    private ArrayList<String> jsonQueueNames = new ArrayList<String>();
    private ArrayList<JSONObject> jsonQueueObjects = new ArrayList<JSONObject>();
    private Drawable soundDrawable = null;
    private boolean soundIconClicked = false;
    private View.OnClickListener textClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d(getClass().getName(), "textview was clicked, soundIconClicked is: " + soundIconClicked);
            if (!scrollViewMoved && (!showingEaseButtons || (showingEaseButtons && !settings.isDoubleTapReview())) && !soundIconClicked) {
                flipCard(showingAnswer);
            }
        }
    };
    private SoundClickListener onSoundIconClickListener = new SoundClickListener() {
        @Override
        public void onSoundClick(View v, String soundName) {
            soundIconClicked = true;
            Log.d("Anki", "sound icon clicked " + soundName);
            if (soundName != null && !soundName.isEmpty()) {
                WearMainActivity.fireMessage(CommonIdentifiers.W2P_PLAY_SOUNDS,
                        new JSONArray().put(soundName).toString());
            }
        }
    };
    private Timer screenTimeoutTimer;
    private long lastResetTimeMillis = 0;
    private int numButtons = 4;
    private JSONArray nextReviewTimes;
    private Spanned q, a;
    private boolean buttonsHiddenOnAmbient = false;
    private float gestureButtonVelocity = 1;

    public ReviewFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment.
     *
     * @param settings
     * @return A new instance of fragment ReviewFragment.
     */
    public static ReviewFragment newInstance(Preferences settings, GridViewPager gridViewPager) {
        ReviewFragment fragment = new ReviewFragment();
        Bundle args = new Bundle();
        ReviewFragment.settings = settings;
        ReviewFragment.gridViewPager = gridViewPager;
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Resources r = getResources();
        gestureButtonVelocity = r.getDisplayMetrics().heightPixels / GESTURE_BUTTON_ANIMATION_TIME_MS;
    }

    private void hideButtons() {
        showingEaseButtons = false;

        for (PullButton easeButton : easeButtons) {
            easeButton.setVisibility(View.GONE);
        }
    }

    private void showButtons() {
        if (nextReviewTimes == null) return;
        showingEaseButtons = true;
        try {
            switch (numButtons) {
                case 2:
                    easeButtons[MID].centerX();
                    easeButtons[FAILED].centerX();
                    easeButtons[MID].slideIn(100);
                    easeButtons[FAILED].slideIn(300);
                    easeButtons[FAILED].setText(nextReviewTimes.getString(0));
                    easeButtons[MID].setText(nextReviewTimes.getString(1));
                    break;
                case 3:
                    easeButtons[FAILED].centerX();
                    easeButtons[EASY].left();
                    easeButtons[MID].right();
                    easeButtons[EASY].slideIn(100);
                    easeButtons[MID].slideIn(300);
                    easeButtons[FAILED].slideIn(500);
                    easeButtons[FAILED].setText(nextReviewTimes.getString(0));
                    easeButtons[MID].setText(nextReviewTimes.getString(1));
                    easeButtons[EASY].setText(nextReviewTimes.getString(2));

                    break;
                case 4:
                    easeButtons[EASY].left();
                    easeButtons[MID].right();
                    easeButtons[HARD].left();
                    easeButtons[FAILED].right();
                    easeButtons[EASY].slideIn(100);
                    easeButtons[MID].slideIn(300);
                    easeButtons[HARD].slideIn(500);
                    easeButtons[FAILED].slideIn(700);
                    easeButtons[FAILED].setText(nextReviewTimes.getString(0));
                    easeButtons[HARD].setText(nextReviewTimes.getString(1));
                    easeButtons[MID].setText(nextReviewTimes.getString(2));
                    easeButtons[EASY].setText(nextReviewTimes.getString(3));
                    break;
            }
        } catch (JSONException e) {
        }
    }

    private void showAnswer() {
        qaScrollView.scrollTo(0, 0);
        showingAnswer = true;
        updateFromHtmlText();
        if (!showingEaseButtons) {
            showButtons();
            sendReviewStateToPhone();
        }
    }

    private void showQuestion() {
        qaScrollView.scrollTo(0, 0);
        showingAnswer = false;
        updateFromHtmlText();
        if (!showingEaseButtons) {
            sendReviewStateToPhone();
        }
    }

    private void sendReviewStateToPhone() {
        if (settings.getPlaySound() != 1 || playSounds == 0) return;

        JSONArray sounds = findSounds(showingAnswer);
        if (sounds.length() >= 1) {
            if (playSounds == -1) {
                if (settings.askBeforeFirstSound()) {
                    showSoundAlertDialog();
                    return;
                } else {
                    playSounds = 1;
                }
            }

            WearMainActivity.fireMessage(CommonIdentifiers.W2P_PLAY_SOUNDS, sounds.toString());
        }
    }

    private void showSoundAlertDialog() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Sounds")
                .setMessage("Should sound files automatically start playing?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        playSounds = 1;
                        sendReviewStateToPhone();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        playSounds = 0;
                        sendReviewStateToPhone();
                    }
                })
                .setIcon(android.R.drawable.ic_media_play)
                .show();
    }

    private JSONArray findSounds(boolean answer) {
        String text;
        if (answer) {
            text = aHtml;
        } else {
            text = qHtml;
        }
        JSONArray jsonArray = new JSONArray();
        Matcher m = fSoundRegexps.matcher(text);
        while (m.find()) {
            jsonArray.put(m.group(2));
        }
        return jsonArray;
    }

    private void updateFromHtmlText() {
        if (showingAnswer) {
            mTextView.setText(a);
        } else {
            mTextView.setText(q);
        }
        makeLinksFocusable(mTextView);
    }

    private void flipCard(final boolean isShowingAnswer) {
        rotationTarget.setRotationY(0);
        if (settings.isFlipCardsAnimationActive()) {
            final int rightOrLeftFlip = isShowingAnswer ? 1 : -1;

            rotationTarget.animate().setDuration(duration).rotationY(rightOrLeftFlip * 90).setListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    updateText(isShowingAnswer);
                    rotationTarget.setRotationY(rightOrLeftFlip * 270);
                    rotationTarget.animate().rotationY(rightOrLeftFlip * 360).setListener(null);
                }
            });
        } else {
            updateText(isShowingAnswer);
        }

    }

    private void updateText(boolean isShowingAnswer) {
        if (isShowingAnswer) showQuestion();
        else showAnswer();
    }

    private int getRealEase(int ease) {
        if (ease == 1 || numButtons == 4) return ease;

        if (numButtons == 2 && ease != 1) return 2;

        if (numButtons == 3 && ease != 1) return ease - 1;

        throw new Error("Illegal ease mode");
    }

    private void blockControls() {
        hideButtons();
        qaOverlay.setOnClickListener(null);
    }

    private void unblockControls() {
        qaOverlay.setOnClickListener(textClickListener);
    }

    public void applySettings() {
        if (settings == null || mTextView == null || !isAdded()) return;
        resetScreenTimeout(true);
        mTextView.setTextSize(settings.getCardFontSize());
        setDayMode(settings.isDayMode());
    }

    private void setDayMode(boolean dayMode){
        if (dayMode) {
            mTextView.setTextColor(getResources().getColor(R.color.dayTextColor));
            qaContainer.setBackgroundResource(R.drawable.round_rect_day);
        } else {
            mTextView.setTextColor(getResources().getColor(R.color.nightTextColor));
            qaContainer.setBackgroundResource(R.drawable.round_rect_night);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mContext = inflater.getContext();
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_review, container, false);

        final WatchViewStub stub = (WatchViewStub) view.findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                qaOverlay = (RelativeLayout) stub.findViewById(R.id.questionAnswerOverlay);
                qaScrollView = (ScrollView) stub.findViewById(R.id.questionAnswerScrollView);
                qaContainer = (RelativeLayout) stub.findViewById(R.id.qaContainer);
                spinner = (ProgressBar) stub.findViewById(R.id.loadingSpinner);
                rotationTarget = qaContainer;
                final GestureDetector gestureDetector = new GestureDetector(getActivity()
                        .getBaseContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            @Override
                            public boolean onDoubleTap(MotionEvent e) {
                                if (showingEaseButtons) {
                                    if (settings.isDoubleTapReview()) {
                                        flipCard(showingAnswer);
                                    }
                                }
                                return false;
                            }
                        });

                qaOverlay.setOnTouchListener(new View.OnTouchListener() {

                    private final float SCROLL_THRESHOLD = ViewConfiguration.get(getActivity()
                            .getBaseContext())
                            .getScaledTouchSlop();
                    private float mDownX;
                    private float mDownY;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
//                        Log.v("test", "ontouchevent " + event.getAction());


                        resetScreenTimeout(false);

//                        soundIconClicked = false;
//                        if (mTextView.onTouchEvent(event)) {
//                            Log.d(getClass().getName(), "textview was touched, soundIconClicked is: " + soundIconClicked);
//                            if (soundIconClicked) return false;
//                        }


                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            mDownX = event.getX();
                            mDownY = event.getY();
                            scrollViewMoved = false;
                        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {

                            if ((Math.abs(mDownX - event.getX()) > SCROLL_THRESHOLD || Math.abs(
                                    mDownY - event.getY()) > SCROLL_THRESHOLD)) {
                                scrollViewMoved = true;
                            }
//                            if(Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD){
//                                gridViewPager.requestDisallowInterceptTouchEvent(true);
//                            }else if(Math.abs(mDownX - event.getX()) > SCROLL_THRESHOLD){
//                                gridViewPager.requestDisallowInterceptTouchEvent(false);
//                            }
                        }
//                        else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
//                            gridViewPager.requestDisallowInterceptTouchEvent(false);
//                        }
                        gestureDetector.onTouchEvent(event);

                        soundIconClicked = false;
                        qaScrollView.dispatchTouchEvent(event);
                        Log.d(getClass().getName(),
                                "textview was touched, soundIconClicked is: " + soundIconClicked);
                        if (soundIconClicked) return false;

                        return false;

                    }
                });


                easeButtons = new PullButton[4];
                easeButtons[EASY] = (PullButton) stub.findViewById(R.id.easyButton);
                easeButtons[MID] = (PullButton) stub.findViewById(R.id.midButton);
                easeButtons[HARD] = (PullButton) stub.findViewById(R.id.hardButton);
                easeButtons[FAILED] = (PullButton) stub.findViewById(R.id.failedButton);

                View.OnClickListener easeButtonListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        resetScreenTimeout(false);
                        int ease = 0;
                        switch (v.getId()) {
                            case R.id.failedButton:
                                ease = getRealEase(1);
                                break;
                            case R.id.hardButton:
                                ease = getRealEase(2);
                                break;
                            case R.id.midButton:
                                ease = getRealEase(3);
                                break;
                            case R.id.easyButton:
                                ease = getRealEase(4);
                                break;
                        }


                        answerCard(ease);
                    }
                };


                for (PullButton easeButton : easeButtons) {
                    easeButton.setOnSwipeListener(easeButtonListener);
                }

                applySettings();
                showLoadingSpinner();
            }
        });

        return view;
    }


    private void answerCard(int ease) {
        JSONObject json = new JSONObject();
        try {
            json.put("ease", ease);
            json.put("note_id", noteID);
            json.put("card_ord", cardOrd);
            json.put("deck_id", settings.getSelectedDeck());
            WearMainActivity.fireMessage(CommonIdentifiers.W2P_RESPOND_CARD_EASE, json.toString());
            indicateLoading();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    public void indicateLoading() {
        mTextView.setText("");
        showLoadingSpinner();
        hideButtons();
    }

    public void showLoadingSpinner() {
        if (spinner == null) return;
        spinner.setVisibility(View.VISIBLE);
    }

    public void hideLoadingSpinner() {
        if (spinner == null) return;
        spinner.setVisibility(View.GONE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(getClass().getName(), "ReviewFragment.onAttach");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(getClass().getName(), "ReviewFragment.onStart");
        resetScreenTimeout(true);
        for (int i = 0; i < jsonQueueNames.size(); i++) {
            onJsonReceive(jsonQueueNames.get(i), jsonQueueObjects.get(i));
        }
        jsonQueueNames.clear();
        jsonQueueObjects.clear();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(getClass().getName(), "ReviewFragment.onDetach");
    }

    @Override
    public void onJsonReceive(String path, JSONObject js) {
        if (qaOverlay == null || !isAdded()) {
            jsonQueueNames.add(path);
            jsonQueueObjects.add(js);
            return;
        }

        if (path.equals(CommonIdentifiers.P2W_RESPOND_CARD)) {
            try {

                hideButtons();
                unblockControls();
                qHtml = js.getString("q");
                aHtml = js.getString("a");

                setQA(false);

                noteID = js.getLong("note_id");
                cardOrd = js.getInt("card_ord");
                nextReviewTimes = js.getJSONArray("b");
                numButtons = nextReviewTimes.length();
                hideLoadingSpinner();
                showQuestion();

                setQA(true);
            } catch (JSONException e) {
                Log.e(TAG, "JSONException " + e);
                e.printStackTrace();
            }
        } else if (path.equals(CommonIdentifiers.P2W_NO_MORE_CARDS)) {
            blockControls();
            hideLoadingSpinner();
            mTextView.setText("No more Cards");
        } else if (path.equals(W2W_REMOVE_SCREEN_LOCK)) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d("ReviewFragment", "removing screen lock");
        } else if (path.equals(W2W_RELOAD_HTML_FOR_MEDIA)) {
            setQA(true);
            Log.d("ReviewFragment", "reloading Html for media");
        }
    }

    public void setQA(boolean loadImages) {
        if (qHtml == null || aHtml == null) return;
        if (loadImages) {
            new ImageLoaderTask().execute();
        } else {
            setSpansFromQAHtml(false);
        }
    }

    public void setSpansFromQAHtml(boolean withImages) {
        Matcher m;

        m = fSoundRegexps.matcher(qHtml);
        while (m.find()) {
            String fname = m.group(2);
        }

        q = makeSoundIconsClickable(Html.fromHtml(qHtml.replaceAll
                (SOUND_TAG_REPLACEMENT_REGEX,
                        SOUND_TAG_REPLACEMENT_STRING).replaceAll("</?a.*?>", "")
                , withImages ? imageGetter : null, null), false);
        a = makeSoundIconsClickable(Html.fromHtml(aHtml.replaceAll
                (SOUND_TAG_REPLACEMENT_REGEX,
                        SOUND_TAG_REPLACEMENT_STRING).replaceAll("</?a.*?>", "")
                , withImages ? imageGetter : null, null), true);

    }

    private Spanned makeSoundIconsClickable(Spanned text, boolean isAnswer) {
        JSONArray sounds = findSounds(isAnswer);
        SpannableString qss = new SpannableString(text);
        int soundIndex = 0;


        ImageSpan[] image_spans = qss.getSpans(0, qss.length(), ImageSpan.class);

        for (ImageSpan span : image_spans) {
            if (span.getSource().equals(SOUND_PLACEHOLDER_STRING)) {

                final String image_src = span.getSource();
                final int start = qss.getSpanStart(span);
                final int end = qss.getSpanEnd(span);


                final String soundName;
                String soundName1 = "";
                if (soundIndex < sounds.length()) {
                    try {
                        soundName1 = sounds.getString(soundIndex);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                soundName = soundName1;
                soundIndex++;


                ClickableString click_span = new ClickableString(onSoundIconClickListener, soundName);

                //                ClickableSpan[] click_spans = qss.getSpans(start, end,
                // ClickableSpan.class);
                //
                //                if (click_spans.length != 0) {
                //                    // remove all click spans
                //                    for (ClickableSpan c_span : click_spans) {
                //                        qss.removeSpan(c_span);
                //                    }
                //                }


                qss.setSpan(click_span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            }


//        int soundIndex = 0;
//        for (int i = 0; i < qss.length()-1; i++){
//            if(((int)qss.charAt(i)) == 55357 && ((int)qss.charAt(i+1)) == 56586){
//                String soundName = null;
//                if(soundIndex < sounds.length()){
//                    try {
//                        soundName = sounds.getString(soundIndex);
//                    } catch (JSONException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                qss.setSpan(new ClickableString(onSoundIconClickListener, soundName), i, i+2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
//                soundIndex++;
//            }
//        }
        }
        return qss;
    }

    private void makeLinksFocusable(TextView tv) {
//        MovementMethod m = tv.getMovementMethod();
//        if ((m == null) || !(m instanceof LinkMovementMethod)) {
//            if (tv.getLinksClickable()) {
        tv.setMovementMethod(LinkMovementMethod.getInstance());
//            }
//        }
    }

    synchronized void resetScreenTimeout(boolean forceReset) {

        if (System.currentTimeMillis() - lastResetTimeMillis > 2000 || forceReset) {

            long timeout = settings.getScreenTimeout() * 1000;

            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.d("ReviewFragment", "setting screen lock " + timeout);
            if (screenTimeoutTimer != null) {
                screenTimeoutTimer.cancel();
                screenTimeoutTimer = null;
            }
            if (screenTimeoutTimer == null) {
                screenTimeoutTimer = new Timer();
                screenTimeoutTimer.schedule(new TimerTask() {
                    public void run() {
                        screenTimeoutTimer.cancel();
                        screenTimeoutTimer = null;
                        Intent messageIntent = new Intent();
                        messageIntent.setAction(Intent.ACTION_SEND);
                        messageIntent.putExtra("path", W2W_REMOVE_SCREEN_LOCK);
                        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(messageIntent);
                    }
                }, timeout);
            }
        }
        lastResetTimeMillis = System.currentTimeMillis();
    }

    @Override
    public void onEnterAmbient() {
        if (showingEaseButtons){
            hideButtons();
            buttonsHiddenOnAmbient = true;
        } else {
            buttonsHiddenOnAmbient = false;
        }
        setDayMode(false);
    }

    @Override
    public void onExitAmbient() {
        if(buttonsHiddenOnAmbient){
            showButtons();
        }
        applySettings();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {

        int answerInt = -1;
        switch (keyCode) {

            case KeyEvent.KEYCODE_NAVIGATE_NEXT:
                answerInt = 1;
                break;
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS:
                answerInt = 4;
                break;
        }
        if (answerInt != -1) {
            if (showingAnswer) {
                easeButtons[answerInt - 1].animateOut(gestureButtonVelocity);
//                answerCard(answerInt);
            } else {
                showAnswer();
            }
            return true;
        }
        // If you did not handle it, let it be handled by the next possible element as deemed by the Activity.
        return false;
    }

    interface SoundClickListener {
        void onSoundClick(View view, String soundName);
    }

    private class MyImageGetter implements Html.ImageGetter {

        public Drawable getDrawable(String source) {
            if (source.equals(SOUND_PLACEHOLDER_STRING)) {
                if (soundDrawable == null) {
                    soundDrawable = getResources().getDrawable(R.drawable.ic_volume_down_black_48dp);
                    soundDrawable.setBounds(0, 0, soundDrawable.getIntrinsicWidth(), soundDrawable.getIntrinsicHeight());
                }
                return soundDrawable;
            }

            if (WearMainActivity.availableAssets.containsKey(source)) {
                Bitmap bitmap = WearMainActivity.loadBitmapFromAsset(WearMainActivity.availableAssets.get(source));
                BitmapDrawable bit = new BitmapDrawable(getResources(), bitmap);
                bit.setBounds(0, 0, bit.getIntrinsicWidth(), bit.getIntrinsicHeight());
                return bit;
            } else {
                Drawable d = new ColorDrawable(Color.TRANSPARENT);
                d.setBounds(0, 0, ReviewFragment.this.getView().getWidth() / 2, ReviewFragment.this.getView().getHeight() / 2);
                return d;
            }
        }
    }

    class ImageLoaderTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... nodes) {
            setSpansFromQAHtml(true);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            updateFromHtmlText();
        }


    }

    private class ClickableString extends ClickableSpan {
        private SoundClickListener mListener;
        private String soundName;

        public ClickableString(SoundClickListener listener, String soundName) {
            mListener = listener;
            this.soundName = soundName;
        }

        @Override
        public void onClick(View v) {
            mListener.onSoundClick(v, soundName);
        }


    }
}
