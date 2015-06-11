package com.yannik.sharedvalues;

/**
 * Created by Yannik on 29.04.2015.
 * Common string Identifiers used in the mobile and wear modules
 */
public class CommonIdentifiers {
    public static final String P2W_CHANGE_SETTINGS = "/com.ichi2.wear/change_settings";
    public static final String W2P_REQUEST_SETTINGS = "/com.ichi2.wear/request_settings";
    public static final String W2P_REQUEST_CARD = "/com.ichi2.wear/requestCard";
    public static final String W2P_RESPOND_CARD_EASE = "/com.ichi2.wear/cardEase";
    public static final String W2P_PLAY_SOUNDS = "/com.ichi2.wear/playSounds";
    public static final String P2W_NO_MORE_CARDS = "/com.ichi2.wear/noMoreCards";
    public static final String W2P_REQUEST_DECKS = "/com.ichi2.wear/requestDecks";
    public static final String W2P_CHOOSE_COLLECTION = "/com.ichi2.wear/chooseCollection";

    public static final String P2W_RESPOND_CARD = "/com.ichi2.wear/respondWithCard";

    public static final String P2W_COLLECTION_LIST = "/com.ichi2.wear/collections";
    public static final String W2P_EXITING = "/com.ichi2.wear/exit";


    public static String getUniqueCardIdentifier(Long noteId, int cardOrd){
        return noteId+"_"+cardOrd;
    }

}
