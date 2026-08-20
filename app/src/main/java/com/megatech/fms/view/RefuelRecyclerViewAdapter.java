package com.megatech.fms.view;

import android.app.Activity;
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
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
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
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.RefuelApproachGuard;
import com.megatech.fms.model.REFUEL_ITEM_STATUS;
import com.megatech.fms.model.RefuelItemData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.ImageButton;
import android.widget.PopupMenu;

public class RefuelRecyclerViewAdapter extends RecyclerView.Adapter<RefuelRecyclerViewAdapter.MyViewHolder> implements Filterable {

    private final UserBaseActivity mContext;

    private final List<RefuelItemData> mData;

    private List<RefuelItemData> mDataFiltered;

    private Timer timer ;

    public RefuelRecyclerViewAdapter(UserBaseActivity mContext, List<RefuelItemData> mData) {
        this.mContext = mContext;
        // Giữ bản sao riêng: adapter sống lâu hơn một lần làm mới và setData() có clear() danh
        // sách này, không được đụng vào list mà phía gọi đang giữ.
        this.mData = (mData != null) ? new ArrayList<>(mData) : new ArrayList<>();
        this.mDataFiltered = new ArrayList<>(this.mData);
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
        // ✅ FIX 3: Check both null and size before accessing
        if (mDataFiltered != null && mDataFiltered.size() > 0) {
            RefuelItemData model = mDataFiltered.get(position);

            //holder.itemView.setOnClickListener(subClick);

            holder.bind(model);

//
            holder.itemView.setOnClickListener(mOnClickListener);
            holder.itemView.setTag(mDataFiltered.get(position));
        } else
            Log.e("Error", "Out of bound or null data");
    }

    @Override
    public int getItemCount() {
        // ✅ FIX 4: CRITICAL - Add null check and safe size() call
        return (mDataFiltered != null) ? mDataFiltered.size() : 0;
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
        // ✅ FIX 5: Add null check before calling stream
        if (mData == null) return null;

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

    /**
     * Cập nhật dữ liệu tại chỗ, giữ nguyên vị trí cuộn.
     *
     * <p>Dùng thay cho việc dựng adapter mới rồi setAdapter() — setAdapter() đưa danh sách về
     * đầu, khiến người dùng mất chuyến đang định chọn mỗi lần đồng bộ chạy.
     */
    public void setData(List<RefuelItemData> newData) {
        List<RefuelItemData> copy = (newData != null) ? new ArrayList<>(newData) : new ArrayList<>();
        this.mData.clear();
        this.mData.addAll(copy);
        this.mDataFiltered = new ArrayList<>(this.mData);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence charSequence) {
                String charString = charSequence.toString().toLowerCase();

                List<RefuelItemData> filteredList = new ArrayList<>();

                // ✅ LUÔN filter từ mData gốc, không phải mDataFiltered
                if (mData != null && !mData.isEmpty()) {
                    for (RefuelItemData row : mData) {
                        if (row.getFlightCode().toLowerCase().contains(charString) ||
                                row.getAircraftCode().toLowerCase().contains(charString)) {
                            filteredList.add(row);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = filteredList;
                return filterResults;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values != null) {
                    mDataFiltered = (ArrayList<RefuelItemData>) results.values;
                } else {
                    mDataFiltered = new ArrayList<>();
                }
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

            // TIẾP CẬN
            binding.btnApproach.setOnClickListener(v -> {
                // Đọc Room để biết còn chuyến nào đang mở dở, nên phải ra thread nền trước
                // khi ghi mốc tiếp cận.
                new Thread(() -> {
                    List<RefuelItemData> blocking =
                            RefuelApproachGuard.findBlocking(itemData.getUniqueId());

                    Context context = itemView.getContext();
                    if (!(context instanceof Activity)) return;
                    Activity activity = (Activity) context;

                    if (!blocking.isEmpty()) {
                        activity.runOnUiThread(() -> showApproachBlocked(activity, blocking));
                        return;
                    }

                    final Date approachTime = new Date();
                    itemData.setApproachTime(approachTime);
                    // Ngoài màn hình tra nạp, mọi thay đổi đều do người dùng chủ động bấm nên
                    // PHẢI được chấp thuận. Precondition chỉ tồn tại để chặn số đồng hồ nhảy
                    // lung tung trong lúc tra nạp; đem nó ra đây chỉ tạo ra "Cập nhật phiếu chưa
                    // lưu được" trên một thao tác hoàn toàn hợp lệ. Patch đọc bản mới nhất rồi
                    // đặt đúng một trường lên trên, không cần nền cũ.
                    activity.runOnUiThread(() ->
                            patchAndUpdate(itemData, latest -> latest.setApproachTime(approachTime)));
                }).start();
            });

            // RỜI ĐI
            binding.btnLeave.setOnClickListener(v -> {

                Context context = itemView.getContext();
                if (!(context instanceof Activity)) return;
                Activity activity = (Activity) context;

                // ===== LẤY GALLON AN TOÀN =====
                double gallon = 0;
                try {
                    gallon = itemData.getGallon();
                } catch (Exception ignored) {}

                // ===== TRƯỜNG HỢP CHƯA TRA NẠP =====
                if (gallon <= 0) {
                    if (activity.isFinishing()) return;

                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.app_name)
                            .setMessage("Chuyến này chưa tra nạp?")
                            .setCancelable(false)

                            // 🔴 HUỶ TIẾP CẬN
                            .setPositiveButton("Huỷ tiếp cận", (dialog, which) -> {
                                dialog.dismiss();

                                // reset dữ liệu
                                itemData.setApproachTime(null);
                                itemData.setLeaveTime(null);
                                itemData.setLocalModified(true);

                                // 🚀 DB + SYNC CHẠY BACKGROUND
                                new Thread(() -> {
                                    boolean ok = RefuelItemData.isCommitted(
                                            DataHelper.postRefuel(itemData, false));
                                    warnIfNotSaved(ok);

                                    // 🔄 UPDATE UI
                                    activity.runOnUiThread(() -> {
                                        int pos = getAdapterPosition();
                                        if (pos != RecyclerView.NO_POSITION) {
                                            notifyItemChanged(pos);
                                        }
                                    });
                                }).start();
                            })

                            // 🟡 TRỞ VỀ
                            .setNegativeButton("Trở về", (dialog, which) -> dialog.dismiss())
                            .show();

                    return;
                }

                // ===== CÓ TRA NẠP → RỜI ĐI BÌNH THƯỜNG =====
                final Date leaveTime = new Date();
                itemData.setLeaveTime(leaveTime);
                itemData.setLocalModified(true);

                // Cập nhật danh sách NGAY, không chờ ghi xong: trước đây nút phải đợi cả
                // lượt postRefuel rồi mới vẽ lại nên bấm xong thấy như không ăn.
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);

                patchAndUpdate(itemData, latest -> latest.setLeaveTime(leaveTime));
            });




        }

        /**
         * Báo không tiếp cận được vì còn chuyến chưa bấm Rời đi. Chỉ có nút đóng: đây là
         * chặn thật, không phải cảnh báo cho qua.
         */
        private void showApproachBlocked(Activity activity, List<RefuelItemData> blocking) {
            if (activity.isFinishing()) return;

            Logger.appendLog("APPROACH", "Chặn tiếp cận, còn " + blocking.size()
                    + " chuyến chưa rời đi");

            new AlertDialog.Builder(activity)
                    .setTitle(R.string.app_name)
                    .setMessage(RefuelApproachGuard.buildMessage(blocking))
                    .setPositiveButton("Đã hiểu", (dialog, which) -> dialog.dismiss())
                    .setCancelable(false)
                    .show();
        }

        /**
         * Ghi một thao tác chủ động của người dùng ở màn hình danh sách.
         *
         * <p>Đọc bản mới nhất trong Room rồi đặt trường cần đổi lên trên, nên KHÔNG phụ
         * thuộc vào nền chụp lúc mở danh sách — nền đó bị lượt pull nền làm cũ liên tục và
         * là lý do các thao tác Tiếp cận / Rời đi bị báo "chưa lưu được" dù hoàn toàn hợp lệ.
         */
        private void patchAndUpdate(RefuelItemData item,
                                    DataHelper.RefuelPatch change) {
            new Thread(() -> {
                boolean ok = false;
                try {
                    DataHelper.PatchResult result =
                            DataHelper.patchRefuel(item.getUniqueId(), change);
                    ok = result != null && result.applied;
                } catch (Exception ex) {
                    Logger.appendLog("FMS", "Patch phiếu lỗi: " + ex.getMessage());
                }
                warnIfNotSaved(ok);

                Context context = itemView.getContext();
                if (context instanceof Activity) {
                    ((Activity) context).runOnUiThread(() -> {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                    });
                }
            }).start();
        }

        private void saveAndUpdate(RefuelItemData item) {

            new Thread(() -> {
                try {
                    Logger.appendLog("FMS", "Posting refuel update...");
                    warnIfNotSaved(RefuelItemData.isCommitted(
                            DataHelper.postRefuel(item, false)));

                } catch (Exception ex) {
                    Logger.appendLog("ERR", ex.getMessage());
                }

                // Cập nhật UI
                ((Activity) ctx).runOnUiThread(() -> {
                    binding.invalidateAll();
                    notifyDataSetChangedSafe();
                });

            }).start();
        }

        private void notifyDataSetChangedSafe() {
            try {
                if (getAdapterPosition() != RecyclerView.NO_POSITION) {
                    RefuelRecyclerViewAdapter.this.notifyItemChanged(getAdapterPosition());
                } else {
                    RefuelRecyclerViewAdapter.this.notifyDataSetChanged();
                }
            } catch (Exception ignored) {}
        }




        /**
         * Ghi nhận tiếp cận/rời đi bị chặn thì phải báo: các thao tác này ghi thẳng vào
         * phiếu, im lặng bỏ qua là mất dữ liệu mà không ai biết.
         */
        private void warnIfNotSaved(boolean committed) {
            if (committed) return;
            Logger.appendLog("FMS", "Cập nhật phiếu chưa lưu được");
            ((Activity) ctx).runOnUiThread(() ->
                    ctx.showErrorMessage(R.string.error_refuel_save_failed));
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
            if (!RefuelItemData.isCommitted(itemData) || itemData.isLocalModified())
                ctx.showErrorMessage(R.string.sync_error_title, R.string.sync_error);
            else {
                ctx.showMessage(R.string.sync, R.string.sync_completed_message, R.drawable.ic_checked_circle, null);
                binding.getMItem().setLocalModified(false);
                binding.invalidateAll();
            }
        }
    }
}