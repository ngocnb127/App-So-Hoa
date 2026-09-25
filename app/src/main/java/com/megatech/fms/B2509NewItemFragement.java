package com.megatech.fms;

import android.os.AsyncTask;
import android.view.View;

import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.model.BM2509Model;

import java.util.Date;

public class B2509NewItemFragement extends BMFormItemFragment<BM2509Model> {

    private final boolean isNew;

    public B2509NewItemFragement() {
        super(new BM2509Model());
        isNew = true;
        model.setTime(new Date());
        model.setTruckId(FMSApplication.getApplication().getTruckId());
    }

    public B2509NewItemFragement(BM2509Model model) {
        super(model);
        isNew = false;
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.b2509_new;
    }

    @Override
    protected void bindViews(View view) {
        if (!isNew)
            return;
        // điền sẵn [2], [4], [6] theo xe (api/bm2509/truckinfo), chỉ vào ô đang trống
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    DataHelper.fillBM2509Suggestions(model);
                } catch (Exception ignored) {
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void unused) {
                if (binding != null)
                    refresh();
            }
        }.execute();
    }

    @Override
    protected void onFieldClick(int id) {
        if (id == R.id.b2509_time)
            pickTime(model.getTime(), model::setTime);
        else if (id == R.id.b2509_flight)
            chooseFlight(R.string.bm2509_flight_no, model.getFlightNo(), flight -> {
                model.setFlightId(flight.getId());
                model.setFlightNo(flight.getFlightCode());
                model.setAcReg(flight.getAircraftCode());
            }, flightNo -> {
                model.setFlightId(null);
                model.setFlightNo(flightNo);
            });
        else if (id == R.id.b2509_ac_reg)
            editText(R.string.bm2509_ac_reg, model.getAcReg(), model::setAcReg);
        else if (id == R.id.b2509_appearance_cb) {
            model.setAppearanceCheck(true);
            model.setAppearanceOther(null);
            refresh();
        } else if (id == R.id.b2509_appearance_other || id == R.id.b2509_appearance) {
            model.setAppearanceCheck(false);
            refresh();
            editText(R.string.appearance_check, model.getAppearanceOther(), model::setAppearanceOther);
        } else if (id == R.id.b2509_doc_no)
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
            editNumber(R.string.bm2509_temperature, model.getObsTemperature(), model::setObsTemperature);
        else if (id == R.id.b2509_density)
            editNumber(R.string.bm2509_density, model.getObsDensity(), model::setObsDensity);
        else if (id == R.id.b2509_density15)
            editNumber(R.string.bm2509_density15, model.getDensity15(), v -> {
                model.setDensity15(v);
                model.calculate();
            });
        else if (id == R.id.b2509_note)
            editText(R.string.note, model.getNote(), model::setNote);
    }

    @Override
    protected void saveModel() {
        DataHelper.postBM2509(model);
    }
}
