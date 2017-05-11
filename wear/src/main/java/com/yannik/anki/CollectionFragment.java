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
import android.widget.ArrayAdapter;
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

    private static final String ARG_PARAM1 = "collections";
    ArrayList<Deck> decks = new ArrayList<>();
    View collectionListContainer;
    private String[] collectionList;
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
    private ArrayAdapter mAdapter;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public CollectionFragment() {
    }

    public static CollectionFragment newInstance(String[] collectionList) {
        CollectionFragment fragment = new CollectionFragment();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PARAM1, collectionList);
        fragment.setArguments(args);
        return fragment;
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

        if (getArguments() != null) {
            this.collectionList = getArguments().getStringArray(ARG_PARAM1);
        }

        mAdapter = new DayNightArrayAdapter(getActivity(),
                R.layout.collection_list_item, decks);
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
            mListener.onFragmentInteraction(decks.get(position).getID());
        }
    }

    /**
     * The default content for this Fragment has a TextView that is shown when
     * the list is empty. If you would like to change the text, call this method
     * to supply the text it should use.
     */
    public void setEmptyText(CharSequence emptyText) {
        View emptyView = mListView.getEmptyView();

        if (emptyView instanceof TextView) {
            ((TextView) emptyView).setText(emptyText);
        }
    }

    @Override
    public void onJsonReceive(String path, JSONObject js) {

        if (path.equals(CommonIdentifiers.P2W_COLLECTION_LIST)) {

            JSONArray collectionNames = js.names();
            if (collectionNames == null) return;

            decks.clear();

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
                    decks.add(newDeck);
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
    private class DayNightArrayAdapter extends ArrayAdapter<Deck>{

        DayNightArrayAdapter(Context context, int layoutResourceID, List<Deck> decks) {
            super(context, layoutResourceID, decks);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TextView v = (TextView)super.getView(position, convertView, parent);
            if(settings == null || settings.isDayMode()){
                v.setTextColor(getResources().getColor(R.color.dayTextColor));
                v.setBackgroundResource(R.drawable.round_rect_day);
            }else{
                v.setTextColor(getResources().getColor(R.color.nightTextColor));
                v.setBackgroundResource(R.drawable.round_rect_night);
            }

            return v;
        }

    }

    /**
     * Deck is an immutable object.
     */
    private class Deck {
        private final String mName;
        private final String mDeckCounts;
        private long mID;

        public Deck(String parName, long parDeckID, String parDeckCounts) {
            mName = parName;
            mID = parDeckID;
            mDeckCounts = parDeckCounts;
        }

        public long getID() {
            return mID;
        }

        @Override
        public String toString() {
            return mName + "  " + mDeckCounts;
        }
    }
}
