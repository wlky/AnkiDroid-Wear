package com.yannik.anki;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;

import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.yannik.sharedvalues.CommonIdentifiers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.yannik.sharedvalues.CommonIdentifiers.P2W_COLLECTION_LIST_DECK_COUNT;
import static com.yannik.sharedvalues.CommonIdentifiers.P2W_COLLECTION_LIST_DECK_ID;

/**
 * A fragment representing a list of Items.
 * <p/>
 * Large screen devices (such as tablets) are supported by replacing the ListView
 * with a GridView.
 * <p/>
 * Activities containing this fragment MUST implement the {@link OnFragmentInteractionListener}
 * interface.
 */
public class CollectionFragment extends Fragment implements AbsListView.OnItemClickListener,
        WearMainActivity.JsonReceiver, WearMainActivity.AmbientStatusReceiver {

    private static final String TAG = "CollectionFragment";

    /**
     * The list of decks that will be displayed, will be provided by.
     */
    ArrayList<Deck> mDecks = new ArrayList<>();
    View collectionListContainer;
    private OnFragmentInteractionListener mListener;
    private Preferences settings;

    /**
     * The fragment's ListView/GridView.
     */
    private AbsListView mListView;

    /**
     * The Adapter which will be used to populate the ListView/GridView with
     * Views.
     */
    private BaseAdapter mAdapter;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CollectionFragment() {
    }

    public static CollectionFragment newInstance() {
        return new CollectionFragment();
    }

    public void setSettings(Preferences settings) {
        this.settings = settings;
        applySettings();
    }

    public void applySettings() {
        if (settings == null) return;

        setDayMode(settings.isDayMode());

        mAdapter.notifyDataSetChanged();
    }

    public void setDayMode(boolean dayMode) {
        if (dayMode) {
            collectionListContainer.setBackgroundResource(R.drawable.round_rect_day);
        } else {
            collectionListContainer.setBackgroundResource(R.drawable.round_rect_night);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new DayNightArrayAdapter(getActivity(), mDecks);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_collection, container, false);
        collectionListContainer = view.findViewById(R.id.collectionListContainer);

        // Set the adapter
        mListView = view.findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);

        // Set OnItemClickListener so we can be notified on item clicks
        mListView.setOnItemClickListener(this);

        applySettings();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        applySettings();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public void setChooseDeckListener(OnFragmentInteractionListener listener) {
        this.mListener = listener;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (null != mListener) {
            // Notify the active callbacks interface (the activity, if the
            // fragment is attached to one) that an item has been selected.
            mListener.onFragmentInteraction(mDecks.get(position).getID());
        }
    }

    @Override
    public void onJsonReceive(String path, JSONObject js) {

        if (path.equals(CommonIdentifiers.P2W_COLLECTION_LIST)) {

            JSONArray collectionNames = js.names();
            if (collectionNames == null) return;

            mDecks.clear();

            for (int i = 0; i < collectionNames.length(); i++) {
                String colName;
                long deckID;
                String deckCounts;
                try {
                    colName = collectionNames.getString(i);
                    JSONObject deckObject = js.getJSONObject(colName);
                    deckID = deckObject.getLong(P2W_COLLECTION_LIST_DECK_ID);
                    deckCounts = deckObject.getString(P2W_COLLECTION_LIST_DECK_COUNT);
                    Deck newDeck = new Deck(colName, deckID, deckCounts);
                    mDecks.add(newDeck);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            if (mListView == null) {
                return;
            }

            mAdapter.notifyDataSetChanged();

        } else {
            Log.w(TAG, "Received message with un-managed path");
        }
    }

    @Override
    public void onExitAmbient() {
        applySettings();
    }

    @Override
    public void onEnterAmbient() {
        setDayMode(false);
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(long id);
    }

    /**
     * Customised adapter for displaying list of deck names.
     * Supports day and night mode.
     */
    private class DayNightArrayAdapter extends BaseAdapter {

        private final Context mContext;
        private final List<Deck> mDNAADecks;

        private class DeckViewHolder {
            RelativeLayout catLayout;
            TextView catName;
            TextView catNumber;
        }

        DayNightArrayAdapter(Context parContext, List<Deck> parDecks) {
            mContext = parContext;
            mDNAADecks = parDecks;
        }

        @Override
        public int getCount() {
            if (mDNAADecks != null) {
                return mDNAADecks.size();
            } else {
                return 0;
            }
        }

        @Override
        public Object getItem(int position) {
            return mDNAADecks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, View view, @NonNull ViewGroup parent) {
            if (view == null) {
                view = LayoutInflater.from(mContext).inflate(R.layout.collection_list_item, parent, false);
            }

            DeckViewHolder viewHolder = (DeckViewHolder) view.getTag();
            if (viewHolder == null) {
                viewHolder = new DeckViewHolder();
                viewHolder.catLayout = view.findViewById(R.id.colllist__mainLayout);
                viewHolder.catName = view.findViewById(R.id.colllist__textcategory);
                viewHolder.catNumber = view.findViewById(R.id.colllist__textNumber);
                view.setTag(viewHolder);
            }

            // setting here values to the fields of my items from my fan object
            Deck oneDeck = mDNAADecks.get(position);
            viewHolder.catName.setText(oneDeck.getName());
            // Using fromHtml to allow easy one character coloration
            viewHolder.catNumber.setText(Html.fromHtml(sumCountsForDeck(oneDeck)));

            // coloring background
            if (settings == null || settings.isDayMode()) {
                viewHolder.catName.setTextColor(getResources().getColor(R.color.dayTextColor));
                viewHolder.catNumber.setTextColor(getResources().getColor(R.color.dayTextColor));
                viewHolder.catLayout.setBackgroundResource(R.drawable.round_rect_day);
            } else {
                viewHolder.catName.setTextColor(getResources().getColor(R.color.nightTextColor));
                viewHolder.catNumber.setTextColor(getResources().getColor(R.color.nightTextColor));
                viewHolder.catLayout.setBackgroundResource(R.drawable.round_rect_night);
            }

            return view;
        }

        /**
         * Will compute the counts by summing all sub-decks and format it in HTML
         *
         * @param targetDeck deck for which to be compute the sum for him and sub decks
         * @return sums formatted HTML, ready to be displayed
         */
        private String sumCountsForDeck(Deck targetDeck) {
            int numNewCards = 0;
            int numLearningCards = 0;
            int numReviewingCards = 0;
            for (Deck deck : mDNAADecks) {
                if (deck.getName().equals(targetDeck.getName()) || deck.getName().contains(targetDeck.getName() + "::")) {
                    numNewCards += deck.getNewCount();
                    numLearningCards += deck.getLearningCount();
                    numReviewingCards += deck.getReviewCount();
                }
            }

            // format and colorize to produce HTML
            StringBuilder res = new StringBuilder();

            if (numNewCards == 0) {
                res.append("<font color='grey'>0</font>");
            } else {
                res.append("<font color='blue'>");
                res.append(numNewCards);
                res.append("</font>");
            }
            res.append(" ");
            if (numLearningCards == 0) {
                res.append("<font color='grey'>0</font>");
            } else {
                res.append("<font color='red'>");
                res.append(numLearningCards);
                res.append("</font>");
            }
            res.append(" ");
            if (numReviewingCards == 0) {
                res.append("<font color='grey'>0</font>");
            } else {
                res.append("<font color='green'>");
                res.append(numReviewingCards);
                res.append("</font>");
            }

            return res.toString();
        }
    }

    /**
     * Deck is an immutable object.
     * Built using provided JSON.
     */
    private static class Deck {
        /**
         * The deck name. e.g. : "computing::java".
         */
        private String mName;
        /**
         * The unique identifier of this deck. e.g. : "1472977314172".
         */
        private long mID;
        /**
         * The number of cards in this deck with status "new".
         */
        private int mNewCount;
        /**
         * The number of cards in this deck with status "learning".
         */
        private int mLearningCount;
        /**
         * The number of cards in this deck with status "to review".
         */
        private int mReviewCount;

        /**
         * Full params constructor.
         *
         * @param parName       The deck name. e.g. : "computing::java".
         * @param parDeckID     The unique identifier of this deck. e.g. : "1472977314172".
         * @param parDeckCounts The number of cards of each type. e.g. : "[4,3,5]"
         */
        Deck(String parName, long parDeckID, String parDeckCounts) {
            setName(parName);
            setID(parDeckID);
            setDeckCounts(parDeckCounts);
        }

        /**
         * @return The deck name. e.g. : "computing::java".
         */
        public String getName() {
            return mName;
        }

        /**
         * @return The number of cards in this deck with status "new".
         */
        int getNewCount() {
            return mNewCount;
        }

        /**
         * @return The number of cards in this deck with status "learning".
         */
        int getLearningCount() {
            return mLearningCount;
        }

        /**
         * @return The number of cards in this deck with status "to review".
         */
        int getReviewCount() {
            return mReviewCount;
        }

        /**
         * Parse deck counts string.
         *
         * @param parDeckCounts The number of cards of each type [learn, review, new]. e.g. : "[4,3,5]"
         */
        private void setDeckCounts(String parDeckCounts) {
            // These are the deck counts of the Deck. [learn, review, new]
            Pattern pattern = Pattern.compile("\\[([0-9]+),([0-9]+),([0-9]+)\\]");
            Matcher matcher = pattern.matcher(parDeckCounts);
            if (matcher.matches()) {
                mLearningCount = Integer.parseInt(matcher.group(1));
                mReviewCount = Integer.parseInt(matcher.group(2));
                mNewCount = Integer.parseInt(matcher.group(3));
            }
        }

        private void setID(long parID) {
            mID = parID;
        }

        private void setName(String parName) {
            mName = parName;
        }

        /**
         * @return The unique identifier of this deck. e.g. : "1472977314172".
         */
        long getID() {
            return mID;
        }

        @NonNull
        @Override
        public String toString() {
            return "Deck{" + "mName='" + mName + '\'' +
                    ", mID=" + mID +
                    ", mNewCount=" + mNewCount +
                    ", mLearningCount=" + mLearningCount +
                    ", mReviewCount=" + mReviewCount +
                    '}';
        }
    }
}
