package com.megatech.fms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.RefuelViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * A fragment representing a single RefuelItem detail screen.
 * This fragment is either contained in a {@link RefuelItemListActivity}
 * in two-pane mode (on tablets) or a {@link RefuelItemDetailActivity}
 * on handsets.
 */
public class RefuelItemDetailFragment extends Fragment  {
    /**
     * The fragment argument representing the item ID that this fragment
     * represents.
     */
    public static final String ARG_ITEM_ID = "item_id";

    /**
     * The dummy content this fragment is presenting.
     */
    private RefuelItemData mItem;

    private Activity activity ;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public RefuelItemDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            mItem = RefuelViewModel.ITEM_MAP.get(getArguments().getInt(ARG_ITEM_ID));

        }
        this.activity = getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.refuelitem_detail, container, false);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        if (mItem != null) {
             Button btnRefuel =  rootView.findViewById(R.id.refuelitem_detail_bntreffuel);
             btnRefuel.setOnClickListener(new View.OnClickListener() {
                 @Override
                 public void onClick(View v) {
                     refuel(mItem);
                 }
             });


            ((TextView) rootView.findViewById(R.id.new_refuel_flightCode)).setText(mItem.getFlightCode());
            ((TextView) rootView.findViewById(R.id.refuelitem_detail_aircraftCode)).setText(mItem.getAircraftCode());
            ((TextView) rootView.findViewById(R.id.refuelitem_detail_parking)).setText(mItem.getParkingLot());
            ((TextView) rootView.findViewById(R.id.refuelitem_detail_estimateAmount)).setText(String.format("%.2f",mItem.getEstimateAmount()));
            if (mItem.getArrivalTime() != new Date(Long.MIN_VALUE))
            ((TextView) rootView.findViewById(R.id.refuelitem_detail_arrival)).setText(simpleDateFormat.format(mItem.getArrivalTime()));
            if (mItem.getDepartureTime() != new Date(Long.MIN_VALUE))
            ((TextView) rootView.findViewById(R.id.refuelitem_detail_departure)).setText(simpleDateFormat.format(mItem.getDepartureTime()));


            REFUEL_ITEM_STATUS status = mItem.getStatus();

            if (status == REFUEL_ITEM_STATUS.DONE)
            {
                btnRefuel.setVisibility(View.GONE);
                if (mItem.getStartTime()!=null )
                    ((TextView) rootView.findViewById(R.id.refuelitem_detail_startTime)).setText(simpleDateFormat.format(mItem.getStartTime()));

                ((TextView) rootView.findViewById(R.id.refuelitem_detail_realAmount)).setText(String.format("%.2f",mItem.getRealAmount()));
            }

        }

        return rootView;
    }

    private void refuel(RefuelItemData mItem) {
        if (mItem.getStatus() == REFUEL_ITEM_STATUS.DONE) {
            Toast.makeText(this.getContext(), "INFO: " + getString(R.string.refuel_item_already_fueled), Toast.LENGTH_LONG).show();

        }
        else {
            Intent intent = new Intent(this.getContext(), RefuelActivity.class);
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

            intent.putExtra("REFUEL_ID", mItem.getId());
            intent.putExtra("REFUEL",gson.toJson(mItem));
            startActivityForResult(intent, mItem.getId());
        }
    }

}
