package com.yannik.anki;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
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

    /** The list of decks that will be displayed, will be provided by. */
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

    public void setSettings(Preferences settings){
        this.settings = settings;
        applySettings();
    }

    public void applySettings(){
        if (settings == null) return;

        setDayMode(settings.isDayMode());

        mAdapter.notifyDataSetChanged();
    }

    public void setDayMode(boolean dayMode){
        if(dayMode) {
            collectionListContainer.setBackgroundResource(R.drawable.round_rect_day);
        }else{
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
        mListView = (AbsListView) view.findViewById(android.R.id.list);
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

    public void setChooseDeckListener(OnFragmentInteractionListener listener){
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
            if(mListView == null) {
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
    private class DayNightArrayAdapter extends BaseAdapter{

        private final Context mContext;
        private final List<Deck> mDNAADecks;

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
                viewHolder.catLayout = (RelativeLayout) view.findViewById(R.id.colllist__mainLayout);
                viewHolder.catName = (TextView) view.findViewById(R.id.colllist__textcategory);
                viewHolder.catNumber = (TextView) view.findViewById(R.id.colllist__textNumber);
                view.setTag(viewHolder);
            }

            // setting here values to the fields of my items from my fan object
            Deck oneDeck = mDNAADecks.get(position);
            viewHolder.catName.setText(oneDeck.getName());
            viewHolder.catNumber.setText(oneDeck.getDeckCounts());

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

        private class DeckViewHolder {
            public RelativeLayout catLayout;
            public TextView catName;
            public TextView catNumber;
        }
    }

    /**
     * Deck is an immutable object.
     * Build using provided JSON.
     */
    private class Deck {
        private final String mName;
        private final String mDeckCounts;
        private long mID;

        Deck(String parName, long parDeckID, String parDeckCounts) {
            mName = parName;
            mID = parDeckID;
            mDeckCounts = parDeckCounts;
        }

        public String getName() {
            return mName;
        }

        public String getDeckCounts() {
            return mDeckCounts;
        }

        long getID() {
            return mID;
        }

        @Override
        public String toString() {
            return mName + "  " + mDeckCounts;
        }
    }
}
