package com.megatech.fms.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.megatech.fms.FMSApplication;
import com.megatech.fms.R;
import com.megatech.fms.RefuelDetailActivity;
import com.megatech.fms.RefuelPreviewActivity;
import com.megatech.fms.UserBaseActivity;
import com.megatech.fms.databinding.CardviewRefuelItemBinding;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class RefuelRecyclerViewAdapter extends RecyclerView.Adapter<RefuelRecyclerViewAdapter.MyViewHolder> implements Filterable {

    private final UserBaseActivity mContext;

    private final List<RefuelItemData> mData;

    private List<RefuelItemData> mDataFiltered;

    private Timer timer ;

    public RefuelRecyclerViewAdapter(UserBaseActivity mContext, List<RefuelItemData> mData) {
        this.mContext = mContext;
        this.mData = mData;
        this.mDataFiltered = mData;
        /*timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

            }
        }, 0, 1000);*/

    }


    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        //View view;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        //view = inflater.inflate(R.layout.cardview_refuel_item,parent,false);
        //return new MyViewHolder(view);
        CardviewRefuelItemBinding itemBinding = CardviewRefuelItemBinding.inflate(inflater, parent, false);
        return new MyViewHolder(itemBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        if (mDataFiltered.size() > 0) {
            RefuelItemData model = mDataFiltered.get(position);

            //holder.itemView.setOnClickListener(subClick);

            holder.bind(model);

//
            holder.itemView.setOnClickListener(mOnClickListener);
            holder.itemView.setTag(mDataFiltered.get(position));
        } else
            Log.e("Error", "Out of bound");
    }

    @Override
    public int getItemCount() {
        return mDataFiltered.size();
    }

    int REQUEST_CODE = 5546;
    int dlgResult = 0;
    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Bundle arguments = new Bundle();
            RefuelItemData item = (RefuelItemData) v.getTag();
            Context context = v.getContext();
            if (item.getFlightStatus() == RefuelItemData.FLIGHT_STATUS.CANCELLED) {
                Toast.makeText(mContext, R.string.cancelled_alert, Toast.LENGTH_LONG).show();
                return;
            }
            if (item.getStatus() != REFUEL_ITEM_STATUS.DONE) {
                if (!item.getTruckNo().equals(FMSApplication.getApplication().getTruckNo())) {
                    RefuelItemData exactItem = findCorrectItem(item);
                    if (exactItem !=null)
                        showRefuel(exactItem);
                    else {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(R.string.app_name)
                                .setMessage(R.string.assigned_to_another_truck)
                                .setPositiveButton(R.string.refuel, (dialog, which) -> {
                                    dialog.dismiss();
                                    showRefuel(item);
                                })
                                .setNegativeButton(R.string.back, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dlgResult = -1;
                                        dialog.dismiss();
                                    }
                                });
                        builder.create().show();

                        if (dlgResult != 0)
                            return;
                    }
                } else
                    showRefuel(item);
            } else if (item.getStatus() == REFUEL_ITEM_STATUS.DONE)
                showPreview(item);


        }
    };

    private RefuelItemData findCorrectItem(RefuelItemData item) {
        return mData.stream().filter(rf->rf.getFlightId() == item.getFlightId() &&
                rf.getTruckId() == FMSApplication.getApplication().getTruckId() &&
                (rf.getStatus() == REFUEL_ITEM_STATUS.NONE || rf.getStatus() == REFUEL_ITEM_STATUS.PROCESSING)

        ).findFirst().orElse(null);
    }

    private void showRefuel(RefuelItemData item) {
        Intent intent = new Intent(mContext, RefuelDetailActivity.class);
        intent.putExtra("REFUEL_ID", item.getId());
        intent.putExtra("REFUEL_LOCAL_ID", item.getLocalId());
        intent.putExtra("REFUEL_UNIQUE_ID", item.getUniqueId());
        intent.putExtra("FLIGHT_ID", item.getFlightId());
        mContext.startActivityForResult(intent, REQUEST_CODE);
    }

    private void showPreview(RefuelItemData item) {
        Intent intent = new Intent(mContext, RefuelPreviewActivity.class);
        intent.putExtra("REFUEL_ID", item.getId());
        intent.putExtra("REFUEL_LOCAL_ID", item.getLocalId());
        intent.putExtra("REFUEL_UNIQUE_ID", item.getUniqueId());
        mContext.startActivityForResult(intent, REQUEST_CODE);
    }


    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString();

                if (charString.isEmpty()) {
                    mDataFiltered = mData;
                } else {
                    List<RefuelItemData> filteredList = new ArrayList<RefuelItemData>();
                    for (RefuelItemData row : mData) {

                        // name match condition. this might differ depending on your requirement
                        // here we are looking for name or phone number match
                        if (row.getFlightCode().toLowerCase().contains(charString.toLowerCase())
                        || row.getAircraftCode().toLowerCase().contains(charString.toLowerCase())) {
                            filteredList.add(row);
                        }
                    }

                    mDataFiltered = filteredList;
                }
                FilterResults filterResults = new FilterResults();
                filterResults.values = mDataFiltered;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

                mDataFiltered = (ArrayList<RefuelItemData>) results.values;
                notifyDataSetChanged();

            }
        };
    }

    public class MyViewHolder extends RecyclerView.ViewHolder {
        TextView mFlightCode;
        TextView mAircraftCode;
        TextView mParkingLot;
        TextView mRefuelTime;
        CheckedTextView mCheck;
        CheckedTextView mSync;
        private CardviewRefuelItemBinding binding;
        UserBaseActivity ctx;

        public MyViewHolder(CardviewRefuelItemBinding binding, @NonNull View itemView) {
            super(itemView);
            this.binding = binding;
        }

        public MyViewHolder(CardviewRefuelItemBinding binding) {
            super(binding.getRoot());
            mSync = binding.getRoot().findViewById(R.id.refuel_item_sync);
            mRefuelTime = binding.getRoot().findViewById(R.id.refuel_item_refuel_time);

            ctx = (UserBaseActivity)binding.getRoot().getContext();
            mSync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    RefuelItemData itemData = binding.getMItem();
                    if (itemData != null) {
                        ctx.setProgressDialog();
                        postData(itemData);
                    }
                }
            });
            this.binding = binding;
        }

        public MyViewHolder(@NonNull View itemView) {
            super(itemView);

            mFlightCode = itemView.findViewById(R.id.refuel_item_flightCode);
            mAircraftCode = itemView.findViewById(R.id.refuel_item_aircraftCode);
            mParkingLot = itemView.findViewById(R.id.refuel_item_parlingLot);
            mCheck = itemView.findViewById(R.id.refuel_item_chk);
            mSync = itemView.findViewById(R.id.refuel_item_sync);
            mRefuelTime = itemView.findViewById(R.id.refuel_item_refuel_time);

        }

        RefuelItemData mData ;
        public void bind(RefuelItemData itemData) {
            binding.setMItem(itemData);
            mData = itemData;

            binding.executePendingBindings();
            Date d = new Date();
            long t = d.getTime() - mData.getRefuelTime().getTime();
            long m = t / (1000 * 60);
            int bgColor = Color.WHITE;
            int color = Color.BLACK;
            if (m > -70 && m <= -10) {
                color = Color.WHITE;
                bgColor = Color.rgb(00,80,00);
            } else if (m > -10 && m <= 10) {
                color = Color.BLUE;
                bgColor = Color.rgb(255, 165, 0);
            } else if (m > 10) {
                color = Color.WHITE;
                bgColor = Color.RED;
            }
            if (mData.getStatus() == REFUEL_ITEM_STATUS.NONE) {
                mRefuelTime.setBackgroundColor(bgColor);
                mRefuelTime.setTextColor(color);
            }

        }

        private void postData(RefuelItemData itemData) {
            new AsyncTask<Void, Void, RefuelItemData>() {
                @Override
                protected RefuelItemData doInBackground(Void... voids) {
                    return DataHelper.postRefuel(itemData, true);
                }

                @Override
                protected void onPostExecute(RefuelItemData itemData) {
                    postDataCompleted(itemData);
                    super.onPostExecute(itemData);

                }
            }.execute();
        }

        private void postDataCompleted(RefuelItemData itemData) {
            ctx.closeProgressDialog();
            if (itemData == null || itemData.isLocalModified())
                ctx.showErrorMessage(R.string.sync_error_title, R.string.sync_error);
            else {
                ctx.showMessage(R.string.sync, R.string.sync_completed_message, R.drawable.ic_checked_circle, null);
                binding.getMItem().setLocalModified(false);
                binding.invalidateAll();
            }
        }
    }


}
