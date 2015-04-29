package com.yannik.ankidroid_wear;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.wearable.view.WatchViewStub;
import android.text.Html;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yannik.sharedvalues.CommonIdentifiers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Timer;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ReviewFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ReviewFragment extends Fragment implements WearMainActivity.JsonReceiver {

    public static TextView mTextView;

    private long noteID;
    private int cardOrd;
    private RelativeLayout qaOverlay;
    private PullButton failed, hard, mid, easy;
    private boolean showingEaseButtons = false, showingAnswer = false;
    private Timer easeButtonShowTimer = new Timer();

    private ScrollView qaScrollView;
    private boolean scrollViewMoved;


    /**
     * Use this factory method to create a new instance of
     * this fragment.
     *
     * @param settings
     * @return A new instance of fragment ReviewFragment.
     */
    public static ReviewFragment newInstance(Preferences settings) {
        ReviewFragment fragment = new ReviewFragment();
        Bundle args = new Bundle();
        ReviewFragment.settings = settings;
        fragment.setArguments(args);
        return fragment;
    }

    public ReviewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void hideButtons() {
        showingEaseButtons = false;
        easy.setVisibility(View.GONE);
        mid.setVisibility(View.GONE);
        hard.setVisibility(View.GONE);
        failed.setVisibility(View.GONE);
    }

    private void showButtons() {
        if (nextReviewTimes == null) return;
        showingEaseButtons = true;
        try {
            switch (numButtons) {
                case 2:
                    mid.centerX();
                    failed.centerX();
                    mid.slideIn(100);
                    failed.slideIn(300);
                    failed.setText(nextReviewTimes.getString(0));
                    mid.setText(nextReviewTimes.getString(1));
                    break;
                case 3:
                    failed.centerX();
                    easy.left();
                    mid.right();
                    easy.slideIn(100);
                    mid.slideIn(300);
                    failed.slideIn(500);
                    failed.setText(nextReviewTimes.getString(0));
                    mid.setText(nextReviewTimes.getString(1));
                    easy.setText(nextReviewTimes.getString(2));

                    break;
                case 4:
                    easy.left();
                    mid.right();
                    hard.left();
                    failed.right();
                    easy.slideIn(100);
                    mid.slideIn(300);
                    hard.slideIn(500);
                    failed.slideIn(700);
                    failed.setText(nextReviewTimes.getString(0));
                    hard.setText(nextReviewTimes.getString(1));
                    mid.setText(nextReviewTimes.getString(2));
                    easy.setText(nextReviewTimes.getString(3));
                    break;
            }
        } catch (JSONException e) {
        }
    }


    private void showAnswer() {
        qaScrollView.scrollTo(0, 0);
        mTextView.setText(a);
        showingAnswer = true;
        if (!showingEaseButtons) {
            showButtons();
        }
    }

    private void showQuestion() {
        qaScrollView.scrollTo(0, 0);
        mTextView.setText(q);
        showingAnswer = false;
    }


    private View rotationTarget;
    private long duration = 180;

    private void flipCard(final boolean isShowingAnswer) {
        rotationTarget.setRotationY(0);
        if(settings.isFlipCardsAnimationActive()) {
            final int rightOrLeftFlip = isShowingAnswer ? 1 : -1;

            rotationTarget.animate().setDuration(duration).rotationY(rightOrLeftFlip * 90).setListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    updateText(isShowingAnswer);
                    rotationTarget.setRotationY(rightOrLeftFlip * 270);
                    rotationTarget.animate().rotationY(rightOrLeftFlip * 360).setListener(null);
                }
            });
        }else {
            updateText(isShowingAnswer);
        }

    }

    private void updateText(boolean isShowingAnswer){
        if (isShowingAnswer) showQuestion(); else showAnswer();
    }

    private int getRealEase(int ease){
        if(ease == 1 || numButtons == 4)return ease;

        if(numButtons == 2 && ease != 1)return 2;

        if(numButtons == 3 && ease != 1)return ease-1;

        throw new Error("Illegal ease mode");
    }

    private void blockControls() {
        hideButtons();
        qaOverlay.setOnClickListener(null);
    }

    private void unblockControls() {
        qaOverlay.setOnClickListener(textClickListener);
    }


    private View.OnClickListener textClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!scrollViewMoved && (!showingEaseButtons || (showingEaseButtons && !settings.isDoubleTapReview()))) {
                flipCard(showingAnswer);
            }
        }
    };

    ObjectAnimator animator;

    private static Preferences settings;

    public void setSettings(Preferences settings) {
        ReviewFragment.settings = settings;
    }

    public void applySettings() {
        if (settings == null || mTextView == null) return;

        mTextView.setTextSize(settings.getCardFontSize());
    }

    private Context mContext;
    private RelativeLayout qaContainer;

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
                rotationTarget = qaContainer;
                final GestureDetector gestureDetector = new GestureDetector(getActivity().getBaseContext(), new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (showingEaseButtons) {
                            if(settings.isDoubleTapReview()) {
                                flipCard(showingAnswer);
                            }
                        }
                        return false;
                    }
                });


                qaOverlay.setOnClickListener(textClickListener);


                qaOverlay.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
//                        Log.v("test", "ontouchevent " + event.getAction());
                        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                            scrollViewMoved = false;
                        }
                        gestureDetector.onTouchEvent(event);
                        qaScrollView.onTouchEvent(event);
                        return false;

                    }
                });

                qaScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        scrollViewMoved = true;
                    }
                });


                easy = (PullButton) stub.findViewById(R.id.easyButton);
                mid = (PullButton) stub.findViewById(R.id.midButton);
                hard = (PullButton) stub.findViewById(R.id.hardButton);
                failed = (PullButton) stub.findViewById(R.id.failedButton);


                View.OnClickListener easeButtonListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int ease=0;
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

                        JSONObject json = new JSONObject();
                        try {
                            json.put("ease", ease);
                            json.put("note_id", noteID);
                            json.put("card_ord", cardOrd);
                            WearMainActivity.fireMessage(CommonIdentifiers.W2P_RESPOND_CARD_EASE, json.toString());
                            hideButtons();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }


                    }
                };


                failed.setOnSwipeListener(easeButtonListener);
                easy.setOnSwipeListener(easeButtonListener);
                hard.setOnSwipeListener(easeButtonListener);
                mid.setOnSwipeListener(easeButtonListener);

                for (int i = 0; i < jsonQueueNames.size(); i++) {
                    onJsonReceive(jsonQueueNames.get(i), jsonQueueObjects.get(i));
                }

                applySettings();
            }
        });

        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    ArrayList<String> jsonQueueNames = new ArrayList<String>();
    ArrayList<JSONObject> jsonQueueObjects = new ArrayList<JSONObject>();

    @Override
    public void onJsonReceive(String path, JSONObject js) {
        if (qaOverlay == null) {
            jsonQueueNames.add(path);
            jsonQueueObjects.add(js);
            return;
        }

        if (path.equals(CommonIdentifiers.P2W_RESPOND_CARD)) {
            try {

                hideButtons();
                unblockControls();

                q = Html.fromHtml(js.getString("q")).toString();
                a = Html.fromHtml(js.getString("a")).toString();
                noteID = js.getLong("note_id");
                cardOrd = js.getInt("card_ord");
                nextReviewTimes = js.getJSONArray("b");
                numButtons = nextReviewTimes.length();
                showQuestion();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (path.equals(CommonIdentifiers.P2W_NO_MORE_CARDS)) {
            blockControls();
            mTextView.setText("No more Cards");
        }
    }


    int numButtons = 4;
    JSONArray nextReviewTimes;
    String q, a;
}
