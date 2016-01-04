package com.yannik.anki;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.app.Fragment;
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

    private static final String ARG_PARAM1 = "collections";
    ArrayList<String> deckNames = new ArrayList<String>();
    ArrayList<Long> deckIDs = new ArrayList<Long>();
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
                R.layout.collection_list_item, this.deckNames);
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
            mListener.onFragmentInteraction(deckIDs.get(position));
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
            deckNames.clear();
            deckIDs.clear();
            for (int i = 0; i < collectionNames.length(); i++) {
                String colName;
                try {
                    colName = collectionNames.getString(i);
                    deckNames.add(colName);
                    deckIDs.add(js.getLong(colName));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            if(mListView == null) {
                return;
            }

            mAdapter.notifyDataSetChanged();
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

    class DayNightArrayAdapter extends ArrayAdapter<String>{
        public DayNightArrayAdapter(Context context, int resource, List<String> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
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

}
