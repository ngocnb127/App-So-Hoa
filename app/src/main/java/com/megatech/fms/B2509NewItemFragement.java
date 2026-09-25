package com.megatech.fms;

import android.view.View;
import android.widget.Spinner;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.BM2509Model;

import java.util.Date;

public class B2509NewItemFragement extends BMFormItemFragment<BM2509Model> {

    private static final String CB = "C&B";

    public B2509NewItemFragement() {
        super(new BM2509Model());
        FMSApplication app = FMSApplication.getApplication();
        model.setTime(new Date());
        model.setTruckId(app.getTruckId());
        model.setTruckNo(app.getTruckNo());
        model.setOperatorId(app.getUser().getUserId());
        model.setOperatorName(app.getUser().getUserName());
    }

    public B2509NewItemFragement(BM2509Model model) {
        super(model);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.b2509_new;
    }

    @Override
    protected void bindViews(View view) {
        bindUserSpinner((Spinner) view.findViewById(R.id.b2509_operator), model.getOperatorId(), user -> {
            model.setOperatorId(user.getId());
            model.setOperatorName(user.getName());
        });
    }

    @Override
    protected void onFieldClick(int id) {
        if (id == R.id.b2509_time)
            pickTime(model.getTime(), model::setTime);
        else if (id == R.id.b2509_flight)
            selectFlight(flight -> {
                model.setFlightId(flight.getId());
                model.setFlightCode(flight.getFlightCode());
                model.setAircraftCode(flight.getAircraftCode());
            });
        else if (id == R.id.b2509_appearance_cb) {
            model.setAppearanceCheck(CB);
            refresh();
        } else if (id == R.id.b2509_appearance_other || id == R.id.b2509_appearance)
            editText(R.string.appearance_check, CB.equals(model.getAppearanceCheck()) ? "" : model.getAppearanceCheck(),
                    value -> model.setAppearanceCheck(value.isEmpty() ? CB : value));
        else if (id == R.id.b2509_doc_no)
            editText(R.string.bm2509_doc_no, model.getDocumentNo(), model::setDocumentNo);
        else if (id == R.id.b2509_last_density15)
            editNumber(R.string.bm2509_last_density15, model.getLastDensity15(), v -> {
                model.setLastDensity15(v);
                model.calculate();
            });
        else if (id == R.id.b2509_remain_qty)
            editNumber(R.string.bm2509_remain_qty, model.getRemainQuantity(), v -> {
                model.setRemainQuantity(v);
                model.calculate();
            });
        else if (id == R.id.b2509_cert_no)
            editText(R.string.bm2509_cert_no, model.getReleaseCertNo(), model::setReleaseCertNo);
        else if (id == R.id.b2509_tank_density15)
            editNumber(R.string.bm2509_tank_density15, model.getTankDensity15(), v -> {
                model.setTankDensity15(v);
                model.calculate();
            });
        else if (id == R.id.b2509_load_qty)
            editNumber(R.string.bm2509_load_qty, model.getLoadQuantity(), v -> {
                model.setLoadQuantity(v);
                model.calculate();
            });
        else if (id == R.id.b2509_temperature)
            editNumber(R.string.bm2509_temperature, model.getTemperature(), model::setTemperature);
        else if (id == R.id.b2509_density)
            editNumber(R.string.bm2509_density, model.getDensity(), model::setDensity);
        else if (id == R.id.b2509_density15)
            editNumber(R.string.bm2509_density15, model.getDensity15(), v -> {
                model.setDensity15(v);
                model.calculate();
            });
    }

    @Override
    protected void saveModel() {
        DataHelper.postBM2509(model);
    }
}
