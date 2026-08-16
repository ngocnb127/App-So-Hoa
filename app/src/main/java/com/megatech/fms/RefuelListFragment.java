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

import java.util.Date;
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

    /**
     * Chữ ký nội dung danh sách của lần vẽ gần nhất, để bỏ qua những lần làm mới không đổi gì.
     * Kèm mốc thời gian vì màu ô giờ tra nạp phụ thuộc thời điểm hiện tại chứ không chỉ dữ liệu.
     */
    private String lastSignature = null;
    private long lastBindTime = 0;
    private static final long REBIND_INTERVAL = 30 * 1000;

    private void refreshdata(int resultOk) {

        final View view = this.getView();
        final Activity activity = getActivity();
        new Thread(() -> {
            final List<RefuelItemData> data = DataHelper.getRefuelList(_self, 0);
            if (activity != null)
                activity.runOnUiThread(() -> {
                    if (data == null) {
                        Toast.makeText(activity, R.string.no_internet_error, Toast.LENGTH_LONG).show();
                        //this.getActivity().finishAffinity();
                    } else {
                        lstData = data;
                        applyData(view, data);
                    }
                });

        }).start();

    }

    /**
     * Đổ dữ liệu vào adapter đang có thay vì dựng adapter mới.
     *
     * <p>{@code RecyclerView.setAdapter()} đặt lại vị trí cuộn về đầu danh sách. Vì phiên đồng bộ
     * chạy 30 giây một lần và mỗi phiên phát nhiều lần thông báo dữ liệu đổi, gọi lại setAdapter
     * ở mỗi lần làm mới khiến danh sách liên tục nhảy về đầu, người dùng không kịp chọn chuyến.
     */
    private void applyData(View view, List<RefuelItemData> data) {
        if (rv == null && view instanceof RefuelRecyclerView)
            rv = (RefuelRecyclerView) view;

        if (mAdapter == null) {
            mAdapter = new RefuelRecyclerViewAdapter((UserBaseActivity) getActivity(), data);
            if (rv != null)
                rv.setAdapter(mAdapter);
            filter(filterQuery);
            lastSignature = signature(data);
            lastBindTime = System.currentTimeMillis();
            return;
        }

        // View vừa được dựng lại (onCreateView tạo RecyclerView mới): gắn lại adapter đang có,
        // nếu không danh sách sẽ trống vì RecyclerView mới chưa có adapter nào.
        if (rv != null && rv.getAdapter() != mAdapter) {
            mAdapter.setData(data);
            rv.setAdapter(mAdapter);
            filter(filterQuery);
            lastSignature = signature(data);
            lastBindTime = System.currentTimeMillis();
            return;
        }

        String sig = signature(data);
        long now = System.currentTimeMillis();
        // Không đổi gì và vừa vẽ xong: bỏ qua hẳn. Vẫn vẽ lại theo chu kỳ để ô giờ tra nạp
        // đổi màu đúng lúc.
        if (sig.equals(lastSignature) && now - lastBindTime < REBIND_INTERVAL)
            return;

        mAdapter.setData(data);
        if (filterQuery != null && filterQuery.length() > 0)
            filter(filterQuery);   // setData trả mDataFiltered về toàn bộ, phải lọc lại
        lastSignature = sig;
        lastBindTime = now;
    }

    private String signature(List<RefuelItemData> data) {
        if (data == null)
            return "null";
        StringBuilder sb = new StringBuilder(data.size() * 48);
        for (RefuelItemData item : data) {
            if (item == null) {
                sb.append("null\n");
                continue;
            }
            sb.append(item.getUniqueId()).append('|')
                    .append(item.getId()).append('|')
                    .append(item.getStatus()).append('|')
                    .append(item.isLocalModified() ? 1 : 0).append('|')
                    .append(item.getFlightCode()).append('|')
                    .append(item.getAircraftCode()).append('|')
                    .append(item.getParkingLot()).append('|')
                    .append(time(item.getRefuelTime())).append('|')
                    .append(time(item.getApproachTime())).append('|')
                    .append(time(item.getLeaveTime())).append('|')
                    .append(item.getRealAmount()).append('\n');
        }
        return sb.toString();
    }

    private static long time(Date value) {
        return value == null ? 0 : value.getTime();
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
