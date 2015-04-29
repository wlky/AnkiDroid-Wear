package com.yannik.ankidroid_wear;

import android.content.SharedPreferences;

/**
* Created by Yannik on 29.04.2015.
*/
class Preferences {
    private WearMainActivity wearMainActivity;
    private float cardFontSize = 40;
    private boolean doubleTapReview = true;
    private boolean flipCards = true;
    private int soundOptions = 0;

    public static String CARD_FONT_SIZE;
    public static String SOUND_OPTIONS;
    public static String DOUBLE_TAP;
    public static String FLIP_ANIMATION;

    public Preferences(WearMainActivity wearMainActivity){
        this.wearMainActivity = wearMainActivity;
        CARD_FONT_SIZE = wearMainActivity.getResources().getString(R.string.font_size_key);
        SOUND_OPTIONS = wearMainActivity.getResources().getString(R.string.sound_preference_key);
        DOUBLE_TAP = wearMainActivity.getResources().getString(R.string.double_tap_key);
        FLIP_ANIMATION = wearMainActivity.getResources().getString(R.string.card_flip_animation_key);
    }

    public float getCardFontSize() {
        return cardFontSize;
    }

    public void setCardFontSize(float cardFontSize) {
        this.cardFontSize = cardFontSize;
        save();
    }

    public boolean isDoubleTapReview() {
        return doubleTapReview;
    }

    public void setDoubleTapReview(boolean doubleTapReview) {
        this.doubleTapReview = doubleTapReview;
        save();
    }

    public boolean isFlipCardsAnimationActive() {
        return flipCards;
    }

    public void setFlipCards(boolean flipCards) {
        this.flipCards = flipCards;
        save();
    }

    public int getSoundOptions() {
        return soundOptions;
    }

    public void setSoundOptions(int soundOptions) {
        this.soundOptions = soundOptions;
        save();
    }

    private void save(){
        SharedPreferences settings = wearMainActivity.getSharedPreferences(WearMainActivity.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(CARD_FONT_SIZE, getCardFontSize());
        editor.putInt(SOUND_OPTIONS, getSoundOptions());
        editor.putBoolean(DOUBLE_TAP, isDoubleTapReview());
        editor.putBoolean(FLIP_ANIMATION, isFlipCardsAnimationActive());
        editor.commit();
    }

    public void load(){
        SharedPreferences settings = wearMainActivity.getSharedPreferences(WearMainActivity.PREFS_NAME, 0);
        cardFontSize = settings.getFloat(CARD_FONT_SIZE, cardFontSize);
        doubleTapReview = settings.getBoolean(DOUBLE_TAP, doubleTapReview);
        flipCards = settings.getBoolean(FLIP_ANIMATION, flipCards);
        soundOptions = settings.getInt(SOUND_OPTIONS, soundOptions);
    }


}
