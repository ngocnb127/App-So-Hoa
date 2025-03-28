package com.megatech.fms;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.TextView;

import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.RefuelViewModel;
import com.megatech.fms.helpers.LCRReader;


import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link RefuelItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class RefuelItemListActivity extends UserBaseActivity {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    LCRReader lcrReader =null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refuelitem_list);
        setToolbar();

        if (currentApp.isFirstUse())
        {
            setting();
        }
        lcrReader =  LCRReader.create(this,currentApp.getDeviceIP(), 10001, true);
        if (findViewById(R.id.refuelitem_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        View recyclerView = findViewById(R.id.refuelitem_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);

        Timer t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshdata(Activity.RESULT_OK);

            }
        },1000*60,1000*60);

        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshdata(Activity.RESULT_OK);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

            refreshdata(resultCode);

    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(this, RefuelViewModel.ITEMS, mTwoPane));
    }
    public  void refreshdata(final int result)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (result == Activity.RESULT_OK)
                RefuelViewModel.loadItems();
                ((RecyclerView)findViewById(R.id.refuelitem_list)).getAdapter().notifyDataSetChanged();
            }
        });

    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final RefuelItemListActivity mParentActivity;
        private final List<RefuelItemData> mValues;
        private final boolean mTwoPane;
        private final int selectedPos = RecyclerView.NO_POSITION;
        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                RecyclerView parent =(RecyclerView)view.getParent();
                for(int i=1;i<parent.getChildCount();i++)
                {
                    View v = parent.getChildAt(i);
                    v.setBackgroundColor(Color.TRANSPARENT);

                }
                view.setBackgroundColor(Color.LTGRAY);

                RefuelItemData item = (RefuelItemData) view.getTag();
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putInt(RefuelItemDetailFragment.ARG_ITEM_ID, item.getId());
                    RefuelItemDetailFragment fragment = new RefuelItemDetailFragment();
                    fragment.setArguments(arguments);
                    mParentActivity.getSupportFragmentManager().beginTransaction()
                            .replace(R.id.refuelitem_detail_container, fragment)
                            .commit();
                } else {
                    Context context = view.getContext();
                    Intent intent = new Intent(context, RefuelItemDetailActivity.class);
                    intent.putExtra(RefuelItemDetailFragment.ARG_ITEM_ID, item.getId());

                    context.startActivity(intent);
                }
            }
        };

        SimpleItemRecyclerViewAdapter(RefuelItemListActivity parent,
                                      List<RefuelItemData> items,
                                      boolean twoPane) {
            mValues = items;
            mParentActivity = parent;
            mTwoPane = twoPane;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER){
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.refuelitem_list_header, parent, false);
                view.setBackgroundColor(Color.DKGRAY);
                return new HeaderViewHolder(view);
            }
            else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.refuelitem_list_content, parent, false);
                return new ItemViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof ItemViewHolder) {
                ItemViewHolder iHolder = (ItemViewHolder)holder;
                RefuelItemData model = mValues.get(position);
                iHolder.mFlightCodeView.setText(model.getFlightCode());
                iHolder.mAircraftView.setText(model.getAircraftCode());
                iHolder.mParkingLotView.setText(model.getParkingLot());
                if (model.getStatus() == REFUEL_ITEM_STATUS.DONE) {
                    if (model.getPostStatus() == RefuelItemData.ITEM_POST_STATUS.ERROR)
                        iHolder.mCheck.setCheckMarkDrawable(R.drawable.ic_error);
                    else
                        iHolder.mCheck.setCheckMarkDrawable(R.drawable.ic_check);
                    iHolder.mCheck.setChecked(true);
                }
                else
                {
                    iHolder.mCheck.setCheckMarkDrawable(null);
                    iHolder.mCheck.setChecked(false);
                }

                holder.itemView.setTag(mValues.get(position));
                holder.itemView.setOnClickListener(mOnClickListener);
                holder.itemView.setSelected(iHolder.position == position);
            }
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }
        public int getItemViewType(int position) {
            if (position == 0) {
                return TYPE_HEADER;
            }
            return TYPE_ITEM;
        }
        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView mFlightCodeView;
            final TextView mAircraftView;
            final TextView mParkingLotView;
            int position = RecyclerView.NO_POSITION;

            ViewHolder(View view) {
                super(view);
                mFlightCodeView = view.findViewById(R.id.txtFlightCode);
                mAircraftView = view.findViewById(R.id.txtAircraftCode);
                mParkingLotView = view.findViewById(R.id.txtParkingLot);
                position = getAdapterPosition();
            }
        }

        private class HeaderViewHolder extends RecyclerView.ViewHolder {

            public HeaderViewHolder(View view) {
                super(view);

            }
        }

        public class ItemViewHolder extends RecyclerView.ViewHolder {

            final TextView mFlightCodeView;
            final TextView mAircraftView;
            final TextView mParkingLotView;
            final CheckedTextView mCheck;
            int position = RecyclerView.NO_POSITION;

            ItemViewHolder(View view) {
                super(view);
                mFlightCodeView = view.findViewById(R.id.txtFlightCode);
                mAircraftView = view.findViewById(R.id.txtAircraftCode);
                mParkingLotView = view.findViewById(R.id.txtParkingLot);
                mCheck = view.findViewById(R.id.txtCheck);
                position = getAdapterPosition();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lcrReader !=null) {
            lcrReader.destroy();
            lcrReader = null;
        }
    }


}
