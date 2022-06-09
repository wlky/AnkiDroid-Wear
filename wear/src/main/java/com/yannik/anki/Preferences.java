package com.yannik.anki;

import android.content.SharedPreferences;

/**
 * Created by Yannik on 29.04.2015.
 */
class Preferences {
    public static String CARD_FONT_SIZE;
    public static String CARD_MAX_FONT_SIZE;
    public static String CARD_EXTRA_PADDING;
    public static String DOUBLE_TAP;
    public static String FLIP_ANIMATION;
    public static String SELECTED_DECK;
    public static String SCREEN_TIMEOUT;
    public static String PLAY_SOUNDS;
    public static String ASK_BEFORE_FIRST_SOUND;
    public static String DAY_MODE;
    public static String ALTERNATIVE_CARD_MODE;
    public static String AMBIENT_MODE;
    private WearMainActivity wearMainActivity;
    private float cardFontSize = 1;
    private float cardMaxFontSize = 100;
    private float cardExtraPadding = 0;
    private boolean doubleTapReview = true;
    private boolean flipCards = true;
    private int screenTimeout = 60;
    private long selectedDeck = -1;
    private int playSound = 0;
    private boolean askBeforeFirstSound;
    private boolean dayMode = true;
    private boolean altCardMode = true;
    private boolean ambientMode = true;


    public Preferences(WearMainActivity wearMainActivity) {
        this.wearMainActivity = wearMainActivity;
        CARD_FONT_SIZE = wearMainActivity.getResources().getString(R.string.font_size_key);
        CARD_MAX_FONT_SIZE = wearMainActivity.getResources().getString(R.string.max_font_size_key);
        CARD_EXTRA_PADDING = wearMainActivity.getResources().getString(R.string.extra_padding_key);
        DOUBLE_TAP = wearMainActivity.getResources().getString(R.string.double_tap_key);
        FLIP_ANIMATION = wearMainActivity.getResources().getString(R.string.card_flip_animation_key);
        SELECTED_DECK = wearMainActivity.getResources().getString(R.string.selected_deck);
        SCREEN_TIMEOUT = wearMainActivity.getResources().getString(R.string.screen_timeout);
        PLAY_SOUNDS = wearMainActivity.getResources().getString(R.string.play_sounds);
        ASK_BEFORE_FIRST_SOUND = wearMainActivity.getResources().getString(R.string.ask_before_first_sound);
        DAY_MODE = wearMainActivity.getResources().getString(R.string.day_mode);
        ALTERNATIVE_CARD_MODE = wearMainActivity.getResources().getString(R.string.alt_card_mode);
        AMBIENT_MODE = wearMainActivity.getResources().getString(R.string.ambient_mode_key);
    }

    public float getCardFontSize() {
        return cardFontSize;
    }

    public void setCardFontSize(float cardFontSize) {
        this.cardFontSize = cardFontSize;
        save();
    }

    public float getCardMaxFontSize() {
        return cardMaxFontSize;
    }

    public void setCardMaxFontSize(float cardMaxFontSize) {
        this.cardMaxFontSize = cardMaxFontSize;
        save();
    }

    public float getCardExtraPadding() {
        return cardExtraPadding;
    }

    public void setCardExtraPadding(float cardExtraPadding) {
        this.cardExtraPadding = cardExtraPadding;
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


    public long getSelectedDeck() {
        return selectedDeck;
    }

    public void setSelectedDeck(long selectedDeck) {
        this.selectedDeck = selectedDeck;
        save();
    }


    public int getScreenTimeout() {
        return screenTimeout;
    }

    public void setScreenTimeout(int screenTimeout) {
        this.screenTimeout = screenTimeout - 30;
        save();
    }

    private void save() {
        SharedPreferences settings = wearMainActivity.getSharedPreferences(WearMainActivity.PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putFloat(CARD_FONT_SIZE, getCardFontSize());
        editor.putFloat(CARD_MAX_FONT_SIZE, getCardMaxFontSize());
        editor.putFloat(CARD_EXTRA_PADDING, getCardExtraPadding());
        editor.putBoolean(DOUBLE_TAP, isDoubleTapReview());
        editor.putBoolean(FLIP_ANIMATION, isFlipCardsAnimationActive());
        editor.putLong(SELECTED_DECK, selectedDeck);
        editor.putInt(SCREEN_TIMEOUT, screenTimeout);
        editor.putInt(PLAY_SOUNDS, playSound);
        editor.putBoolean(ASK_BEFORE_FIRST_SOUND, askBeforeFirstSound);
        editor.putBoolean(DAY_MODE, dayMode);
        editor.putBoolean(ALTERNATIVE_CARD_MODE, altCardMode);
        editor.putBoolean(AMBIENT_MODE, ambientMode);
        editor.commit();
    }

    public boolean isAmbientMode() {
        return ambientMode;
    }

    public void setAmbientMode(boolean ambientMode) {
        this.ambientMode = ambientMode;
        save();
    }

    public void load() {
        SharedPreferences settings = wearMainActivity.getSharedPreferences(WearMainActivity.PREFS_NAME, 0);
        cardFontSize = settings.getFloat(CARD_FONT_SIZE, cardFontSize);
        cardMaxFontSize = settings.getFloat(CARD_MAX_FONT_SIZE, cardMaxFontSize);
        cardExtraPadding = settings.getFloat(CARD_EXTRA_PADDING, cardExtraPadding);
        doubleTapReview = settings.getBoolean(DOUBLE_TAP, doubleTapReview);
        flipCards = settings.getBoolean(FLIP_ANIMATION, flipCards);
        screenTimeout = settings.getInt(SCREEN_TIMEOUT, screenTimeout);
        selectedDeck = settings.getLong(SELECTED_DECK, selectedDeck);
        playSound = settings.getInt(PLAY_SOUNDS, playSound);
        askBeforeFirstSound = settings.getBoolean(ASK_BEFORE_FIRST_SOUND, askBeforeFirstSound);
        dayMode = settings.getBoolean(ASK_BEFORE_FIRST_SOUND, dayMode);
        altCardMode = settings.getBoolean(ALTERNATIVE_CARD_MODE, altCardMode);
        ambientMode = settings.getBoolean(AMBIENT_MODE, ambientMode);
    }


    public int getPlaySound() {
        return playSound;
    }

    public void setPlaySound(int playSound) {
        this.playSound = playSound;
        save();
    }

    public void setAskBeforeFirstSound(boolean askBeforeFirstSound) {
        this.askBeforeFirstSound = askBeforeFirstSound;
        save();
    }

    public boolean askBeforeFirstSound() {
        return askBeforeFirstSound;
    }

    public boolean isDayMode() {
        return dayMode;
    }

    public void setDayMode(boolean dayMode) {
        this.dayMode = dayMode;
        save();
    }

    public boolean isAltCardMode() {
        return altCardMode;
    }

    public void setAltCardMode(boolean altCardMode) {
        this.altCardMode = altCardMode;
        save();
    }
}
