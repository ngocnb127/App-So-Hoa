package com.megatech.fms;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.view.RefuelRecyclerView;
import com.megatech.fms.view.RefuelRecyclerViewAdapter;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RefuelListFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * create an instance of this fragment.
 */
public class RefuelListFragment extends Fragment {



    List<RefuelItemData> lstData;

    private boolean _self;

    public RefuelListFragment() {
        // Required empty public constructor
    }

    public RefuelListFragment(boolean self) {
        // Required empty public constructor
        _self = self;
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);




    }

    RefuelRecyclerViewAdapter mAdapter;

    public void refresh() {
        refreshdata(Activity.RESULT_OK);
    }

    private void refreshdata(int resultOk) {

        final View view = this.getView();
        final Activity activity = getActivity();
        new Thread(() -> {
            lstData = DataHelper.getRefuelList(_self, 0);
            if (activity != null)
                activity.runOnUiThread(() -> {
                    if (lstData == null) {
                        Toast.makeText(activity, R.string.no_internet_error, Toast.LENGTH_LONG).show();
                        //this.getActivity().finishAffinity();
                    } else {
                        mAdapter = new RefuelRecyclerViewAdapter((UserBaseActivity) getActivity(), lstData);

                        filter(filterQuery);

                        if (rv == null)
                            rv = (RefuelRecyclerView) view;

                        if (rv != null) {
                            rv.getRecycledViewPool().clear();

                            rv.setAdapter(mAdapter);
                        }

                    }
                });

        }).start();

    }

    private CharSequence filterQuery = "";

    public void filter(CharSequence sequence) {
        filterQuery = sequence;
        if (mAdapter != null)
            mAdapter.getFilter().filter(sequence);
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshdata(Activity.RESULT_OK);



    }

    @Override
    public void onPause() {
        super.onPause();
        //timer.cancel();
    }

    View rootview;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        rootview = inflater.inflate(R.layout.fragment_self_refuel, container, false);


        //RecyclerView rv = rootview.findViewById(R.id.refuelitem_list);

        if (activity == null)
            activity = getActivity();
        rv = new RefuelRecyclerView(activity);

        rv.setLayoutManager(new GridLayoutManager(getActivity(), 1));
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    refreshdata(Activity.RESULT_OK);
                }
            });


        return rv;
    }

    protected Activity activity;
    protected RecyclerView rv;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(Uri uri);
    }
}
