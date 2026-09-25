package com.megatech.fms;

import androidx.fragment.app.DialogFragment;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.DateUtils;
import com.megatech.fms.model.BM2509Model;

import java.util.Date;
import java.util.List;

// BM 25.09/NLHK - Phiếu kiểm tra đối chứng nhiên liệu JET A-1 trên xe tra nạp
public class B2509Activity extends BMFormListActivity<BM2509Model> {

    @Override
    protected int getTitleRes() {
        return R.string.bm2509_title;
    }

    @Override
    protected String[] getHeaders() {
        return new String[]{getString(R.string.time), getString(R.string.bm2509_flight),
                getString(R.string.appearance_check), getString(R.string.bm2509_avg_density),
                getString(R.string.bm2509_density_diff)};
    }

    @Override
    protected String[] getColumns(BM2509Model item) {
        String flight = (item.getFlightNo() != null ? item.getFlightNo() : "")
                + (item.getAcReg() != null ? " / " + item.getAcReg() : "");
        String appearance = Boolean.FALSE.equals(item.getAppearanceCheck())
                ? getString(R.string.other) + ": " + (item.getAppearanceOther() != null ? item.getAppearanceOther() : "")
                : "C&B";
        String last = item.getSyncError() != null ? "⚠ " + item.getSyncError() : BM2509Model.num(item.getDensityDiff());
        return new String[]{DateUtils.formatDate(item.getTime(), "HH:mm dd/MM/yyyy"), flight, appearance,
                BM2509Model.num(item.getAverageDensity()), last};
    }

    @Override
    protected List<BM2509Model> getList(Date date) {
        return DataHelper.getBM2509List(date);
    }

    @Override
    protected void delete(int[] ids) {
        DataHelper.deleteBM2509(ids);
    }

    @Override
    protected DialogFragment createItemDialog(BM2509Model model) {
        return model == null ? new B2509NewItemFragement() : new B2509NewItemFragement(model);
    }
}
