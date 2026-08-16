package com.megatech.fms;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.megatech.fms.data.AppDatabase;
import com.megatech.fms.data.BM7501Repository;
import com.megatech.fms.helpers.BM7501Prefill;
import com.megatech.fms.helpers.BM7501Printer;
import com.megatech.fms.helpers.BM7501Signatures;
import com.megatech.fms.helpers.BM7501Validator;
import com.megatech.fms.helpers.DataHelper;
import com.megatech.fms.helpers.Logger;
import com.megatech.fms.helpers.ZebraWorker;
import com.megatech.fms.model.BM7501Model;
import com.megatech.fms.model.BM7501Model.Additive;
import com.megatech.fms.model.BM7501Model.AdditivePresence;
import com.megatech.fms.model.BM7501Model.DefuelMethod;
import com.megatech.fms.model.BM7501Model.DefuelReason;
import com.megatech.fms.model.BM7501Model.HandlingOption;
import com.megatech.fms.model.BM7501Model.MicrobialKit;
import com.megatech.fms.model.BM7501Model.MicrobialResult;
import com.megatech.fms.model.BM7501Model.QcCheck;
import com.megatech.fms.model.BM7501Model.TriState;
import com.megatech.fms.model.RefuelItemData;
import com.megatech.fms.model.TruckModel;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Màn hình nhập BM 75.01 — phiếu yêu cầu hút nhiên liệu.
 *
 * <p>Mở theo MỘT mẻ hút (intent {@code REFUEL_UNIQUE_ID}), vì quan hệ đã chốt là
 * một phiếu ↔ một mẻ. Nút in nằm trong màn hình này chứ không nằm ở danh sách, để luôn
 * biết đang in cho chuyến nào.
 *
 * <p>Nút in chỉ hiện khi {@code BuildConfig.THERMAL_PRINTER} — nhập/lưu thì bản nào cũng làm được.
 */
public class B7501Activity extends UserBaseActivity implements View.OnClickListener {

    public static final String EXTRA_REFUEL_UNIQUE_ID = "REFUEL_UNIQUE_ID";

    private static final String TIME_PATTERN = "HH:mm dd/MM/yyyy";
    private static final String LOG_TAG = "BM7501";

    private BM7501Repository repository;
    private BM7501Model model;
    private String refuelUniqueId;

    private ZebraWorker zebra;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_b7501);

        repository = new BM7501Repository(AppDatabase.getInstance(this).bm7501Dao());

        Intent intent = getIntent();
        refuelUniqueId = intent == null ? null : intent.getStringExtra(EXTRA_REFUEL_UNIQUE_ID);
        if (refuelUniqueId == null || refuelUniqueId.isEmpty()) {
            showErrorMessage(R.string.bm7501_missing_refuel);
            finish();
            return;
        }

        setupSpinners();

        Button btnPrint = findViewById(R.id.btnPrint7501);
        btnPrint.setVisibility(BuildConfig.THERMAL_PRINTER ? View.VISIBLE : View.GONE);

        loadOrCreate();
    }

    // ------------------------------------------------------------------ nạp dữ liệu

    private void loadOrCreate() {
        setProgressDialog();
        new Thread(() -> {
            BM7501Model loaded = null;
            boolean duplicated = false;
            try {
                duplicated = repository.hasMultipleActive(refuelUniqueId);
                loaded = repository.getActive(refuelUniqueId);

                if (loaded == null) {
                    RefuelItemData item = DataHelper.getRefuelItem(refuelUniqueId);
                    BM7501Model created = BM7501Prefill.fromRefuelItem(item);
                    created.setRefuelItemUniqueId(refuelUniqueId);
                    created.setEnteredByUserId(currentUser == null ? 0 : currentUser.getUserId());
                    created.setTruckId(currentApp.getTruckId());
                    loaded = repository.createOrGetActive(created);
                }
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "loadOrCreate: " + ex.getMessage());
            }

            final BM7501Model result = loaded;
            final boolean hasDuplicate = duplicated;
            runOnUiThread(() -> {
                closeProgressDialog();
                if (result == null) {
                    showErrorMessage(R.string.error_loading_item);
                    finish();
                    return;
                }
                model = result;
                bindToForm();
                if (hasDuplicate) {
                    // Bất biến "mỗi mẻ một phiếu hiệu lực" bị vi phạm: khoá ký/in,
                    // không tự chọn bừa một revision.
                    findViewById(R.id.btnPrint7501).setEnabled(false);
                    findViewById(R.id.btnSave7501).setEnabled(false);
                    showErrorMessage(R.string.bm7501_multiple_active);
                    Logger.appendLog(LOG_TAG, "Nhiều phiếu hiệu lực cho mẻ " + refuelUniqueId);
                }
            });
        }, "BM7501-Load").start();
    }

    private void setupSpinners() {
        setSpinner(R.id.f_custMicroKit, kitLabels());
        setSpinner(R.id.f_skypecMicroKit, kitLabels());
        setSpinner(R.id.f_custMicroResult, resultLabels());
        setSpinner(R.id.f_skypecMicroResult, resultLabels());
        setSpinner(R.id.f_handling, handlingLabels());
    }

    private void setSpinner(int id, List<String> labels) {
        Spinner spinner = findViewById(id);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
    }

    private static List<String> kitLabels() {
        List<String> l = new ArrayList<>();
        l.add("-- chưa chọn --");
        l.add("Hy-lite");
        l.add("Microb monitor2");
        l.add("Fuelstat");
        l.add("Khác");
        return l;
    }

    private static List<String> resultLabels() {
        List<String> l = new ArrayList<>();
        l.add("-- chưa chọn --");
        l.add("Được chấp nhận / Normal");
        l.add("Cảnh báo / Warning");
        l.add("Mức độ nặng / Action");
        return l;
    }

    private static List<String> handlingLabels() {
        List<String> l = new ArrayList<>();
        l.add("-- chưa chọn --");
        l.add("Yêu cầu lưu trữ");
        l.add("Nạp lại cho chính tàu bay đã hút");
        l.add("Nạp lại cho tàu bay khác của hãng");
        l.add("Không nạp lại, ủy quyền SKYPEC xử lý");
        l.add("Vẫn nạp lại dù nhiên liệu có vấn đề");
        return l;
    }

    // ------------------------------------------------------------------ model -> form

    private void bindToForm() {
        setText(R.id.f_customerRepName, model.getCustomerRepName());
        setText(R.id.f_customerTitle, model.getCustomerTitle());
        setText(R.id.f_customerTel, model.getCustomerTel());
        setText(R.id.f_customerFax, model.getCustomerFax());
        setText(R.id.f_aircraftType, model.getAircraftType());
        setText(R.id.f_aircraftReg, model.getAircraftReg());

        if (model.getReason() != null) {
            checkRadio(R.id.f_reason, model.getReason() == DefuelReason.LOAD_ADJUSTMENT ? R.id.f_reason_load
                    : model.getReason() == DefuelReason.MAINTENANCE ? R.id.f_reason_maint : R.id.f_reason_other);
        }
        setText(R.id.f_reasonOther, model.getReasonOther());

        if (model.getTankDrainSampled() != null) {
            checkRadio(R.id.f_tankDrain, model.getTankDrainSampled() ? R.id.f_tankDrain_yes : R.id.f_tankDrain_no);
        }
        if (model.getCustomerMicrobialTestPerformed() != null) {
            checkRadio(R.id.f_custMicro,
                    model.getCustomerMicrobialTestPerformed() == TriState.YES ? R.id.f_custMicro_yes
                            : model.getCustomerMicrobialTestPerformed() == TriState.NO ? R.id.f_custMicro_no
                            : R.id.f_custMicro_unk);
        }
        setSpinnerValue(R.id.f_custMicroKit, kitIndex(model.getCustomerMicrobialKit()));
        setText(R.id.f_custMicroKitOther, model.getCustomerMicrobialKitOther());
        setSpinnerValue(R.id.f_custMicroResult, resultIndex(model.getCustomerMicrobialResult()));

        if (model.getAdditivePresence() != null) {
            checkRadio(R.id.f_additivePresence,
                    model.getAdditivePresence() == AdditivePresence.PRESENT ? R.id.f_add_present
                            : model.getAdditivePresence() == AdditivePresence.NONE ? R.id.f_add_none
                            : R.id.f_add_undet);
        }
        List<Additive> additives = model.getAdditives();
        setChecked(R.id.f_add_fsii, additives != null && additives.contains(Additive.FSII));
        setChecked(R.id.f_add_biocide, additives != null && additives.contains(Additive.BIOCIDE));
        setChecked(R.id.f_add_aquarius, additives != null && additives.contains(Additive.AQUARIUS_WMA));

        setText(R.id.f_prevLocation1, model.getPrevLocation1());
        setText(R.id.f_prevGrade1, model.getPrevGrade1());
        setText(R.id.f_prevLocation2, model.getPrevLocation2());
        setText(R.id.f_prevGrade2, model.getPrevGrade2());

        if (model.getVac() != null) {
            checkRadio(R.id.f_vac, model.getVac() == QcCheck.SATISFY ? R.id.f_vac_ok : R.id.f_vac_no);
        }
        if (model.getCwd() != null) {
            checkRadio(R.id.f_cwd, model.getCwd() == QcCheck.SATISFY ? R.id.f_cwd_ok : R.id.f_cwd_no);
        }
        setNumber(R.id.f_densityKgM3, model.getDensityKgM3());
        setChecked(R.id.f_conductivityRequired, model.isConductivityRequired());
        setNumber(R.id.f_conductivityPsM, model.getConductivityPsM());
        setChecked(R.id.f_contaminationSuspected, model.isContaminationSuspected());
        setChecked(R.id.f_customerRequestedMicrobial, model.isCustomerRequestedMicrobial());
        setSpinnerValue(R.id.f_skypecMicroKit, kitIndex(model.getSkypecMicrobialKit()));
        setSpinnerValue(R.id.f_skypecMicroResult, resultIndex(model.getSkypecMicrobialResult()));
        setText(R.id.f_microbialReason, model.getMicrobialReason());

        setText(R.id.f_defuellerTruckNo, model.getDefuellerTruckNo());
        setText(R.id.f_startTime, formatDate(model.getStartTime()));
        setText(R.id.f_endTime, formatDate(model.getEndTime()));
        if (model.getMethod() != null) {
            checkRadio(R.id.f_method, model.getMethod() == DefuelMethod.AIRCRAFT_PUMP ? R.id.f_method_ac
                    : model.getMethod() == DefuelMethod.REFUELLER_PUMP ? R.id.f_method_ref : R.id.f_method_both);
        }
        setChecked(R.id.f_signalsBriefed, model.isSignalsBriefed());
        setText(R.id.f_otherSignal, model.getOtherSignal());
        setNumber(R.id.f_expectedKg, model.getExpectedKg());
        setNumber(R.id.f_actualKg, model.getActualKg());
        setNumber(R.id.f_actualTempC, model.getActualTempC());
        setNumber(R.id.f_actualDensityKgM3, model.getActualDensityKgM3());
        setNumber(R.id.f_gallon, model.getGallon());
        setNumber(R.id.f_liter, model.getLiter());
        if (model.getRefuellableWithoutTest() != null) {
            checkRadio(R.id.f_refuellable, model.getRefuellableWithoutTest()
                    ? R.id.f_refuellable_yes : R.id.f_refuellable_no);
        }
        setSpinnerValue(R.id.f_handling, handlingIndex(model.getHandling()));
        setText(R.id.f_storageFrom, formatDate(model.getStorageFrom()));
        setText(R.id.f_storageTo, formatDate(model.getStorageTo()));
        setText(R.id.f_handlingNote, model.getHandlingNote());
        setText(R.id.f_customerRepFinalName, model.getCustomerRepFinalName());
        setText(R.id.f_skypecRepName, model.getSkypecRepName());

        updateSignatureLabels();
        updateStatusLine();
        setFormEnabled(model.isEditable());
    }

    private void updateStatusLine() {
        TextView status = findViewById(R.id.bm7501_status);
        String number = model.getLocalNumber() == null || model.getLocalNumber().isEmpty()
                ? "(chưa có số phiếu)" : model.getLocalNumber();
        status.setText(getString(R.string.bm7501_status_line, number,
                String.valueOf(model.getBusinessStatus())));
    }

    // ------------------------------------------------------------------ form -> model

    private void bindFromForm() {
        model.setCustomerRepName(fieldText(R.id.f_customerRepName));
        model.setCustomerTitle(fieldText(R.id.f_customerTitle));
        model.setCustomerTel(fieldText(R.id.f_customerTel));
        model.setCustomerFax(fieldText(R.id.f_customerFax));
        model.setAircraftType(fieldText(R.id.f_aircraftType));
        model.setAircraftReg(fieldText(R.id.f_aircraftReg));

        int reason = checkedId(R.id.f_reason);
        model.setReason(reason == R.id.f_reason_load ? DefuelReason.LOAD_ADJUSTMENT
                : reason == R.id.f_reason_maint ? DefuelReason.MAINTENANCE
                : reason == R.id.f_reason_other ? DefuelReason.OTHER : null);
        model.setReasonOther(fieldText(R.id.f_reasonOther));

        int drain = checkedId(R.id.f_tankDrain);
        model.setTankDrainSampled(drain == R.id.f_tankDrain_yes ? Boolean.TRUE
                : drain == R.id.f_tankDrain_no ? Boolean.FALSE : null);

        int micro = checkedId(R.id.f_custMicro);
        model.setCustomerMicrobialTestPerformed(micro == R.id.f_custMicro_yes ? TriState.YES
                : micro == R.id.f_custMicro_no ? TriState.NO
                : micro == R.id.f_custMicro_unk ? TriState.UNKNOWN : null);
        model.setCustomerMicrobialKit(kitAt(selectedIndex(R.id.f_custMicroKit)));
        model.setCustomerMicrobialKitOther(fieldText(R.id.f_custMicroKitOther));
        model.setCustomerMicrobialResult(resultAt(selectedIndex(R.id.f_custMicroResult)));

        int additive = checkedId(R.id.f_additivePresence);
        model.setAdditivePresence(additive == R.id.f_add_present ? AdditivePresence.PRESENT
                : additive == R.id.f_add_none ? AdditivePresence.NONE
                : additive == R.id.f_add_undet ? AdditivePresence.UNDETERMINED : null);
        List<Additive> additives = new ArrayList<>();
        if (isChecked(R.id.f_add_fsii)) additives.add(Additive.FSII);
        if (isChecked(R.id.f_add_biocide)) additives.add(Additive.BIOCIDE);
        if (isChecked(R.id.f_add_aquarius)) additives.add(Additive.AQUARIUS_WMA);
        model.setAdditives(additives);

        model.setPrevLocation1(fieldText(R.id.f_prevLocation1));
        model.setPrevGrade1(fieldText(R.id.f_prevGrade1));
        model.setPrevLocation2(fieldText(R.id.f_prevLocation2));
        model.setPrevGrade2(fieldText(R.id.f_prevGrade2));

        int vac = checkedId(R.id.f_vac);
        model.setVac(vac == R.id.f_vac_ok ? QcCheck.SATISFY
                : vac == R.id.f_vac_no ? QcCheck.NOT_SATISFY : null);
        int cwd = checkedId(R.id.f_cwd);
        model.setCwd(cwd == R.id.f_cwd_ok ? QcCheck.SATISFY
                : cwd == R.id.f_cwd_no ? QcCheck.NOT_SATISFY : null);

        model.setDensityKgM3(getNumber(R.id.f_densityKgM3));
        model.setConductivityRequired(isChecked(R.id.f_conductivityRequired));
        model.setConductivityPsM(getNumber(R.id.f_conductivityPsM));
        model.setContaminationSuspected(isChecked(R.id.f_contaminationSuspected));
        model.setCustomerRequestedMicrobial(isChecked(R.id.f_customerRequestedMicrobial));
        model.setSkypecMicrobialKit(kitAt(selectedIndex(R.id.f_skypecMicroKit)));
        model.setSkypecMicrobialResult(resultAt(selectedIndex(R.id.f_skypecMicroResult)));
        model.setMicrobialReason(fieldText(R.id.f_microbialReason));

        model.setDefuellerTruckNo(fieldText(R.id.f_defuellerTruckNo));
        model.setStartTime(parseDate(fieldText(R.id.f_startTime)));
        model.setEndTime(parseDate(fieldText(R.id.f_endTime)));

        int method = checkedId(R.id.f_method);
        model.setMethod(method == R.id.f_method_ac ? DefuelMethod.AIRCRAFT_PUMP
                : method == R.id.f_method_ref ? DefuelMethod.REFUELLER_PUMP
                : method == R.id.f_method_both ? DefuelMethod.BOTH : null);
        model.setSignalsBriefed(isChecked(R.id.f_signalsBriefed));
        model.setOtherSignal(fieldText(R.id.f_otherSignal));

        model.setExpectedKg(getNumber(R.id.f_expectedKg));
        model.setActualKg(getNumber(R.id.f_actualKg));
        model.setActualTempC(getNumber(R.id.f_actualTempC));
        model.setActualDensityKgM3(getNumber(R.id.f_actualDensityKgM3));
        model.setGallon(getNumber(R.id.f_gallon));
        model.setLiter(getNumber(R.id.f_liter));

        int refuellable = checkedId(R.id.f_refuellable);
        model.setRefuellableWithoutTest(refuellable == R.id.f_refuellable_yes ? Boolean.TRUE
                : refuellable == R.id.f_refuellable_no ? Boolean.FALSE : null);
        model.setHandling(handlingAt(selectedIndex(R.id.f_handling)));
        model.setStorageFrom(parseDate(fieldText(R.id.f_storageFrom)));
        model.setStorageTo(parseDate(fieldText(R.id.f_storageTo)));
        model.setHandlingNote(fieldText(R.id.f_handlingNote));
        model.setCustomerRepFinalName(fieldText(R.id.f_customerRepFinalName));
        model.setSkypecRepName(fieldText(R.id.f_skypecRepName));
    }

    // ------------------------------------------------------------------ hành động

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnBack) {
            save(false);
            finish();
        } else if (id == R.id.btnSave7501) {
            save(true);
        } else if (id == R.id.btnPrint7501) {
            printForm();
        } else if (id == R.id.btnSign7501) {
            signAndFinish();
        } else if (id == R.id.btnSignSectionA) {
            openSign(REQ_SIGN_SECTION_A);
        } else if (id == R.id.btnSignSeller) {
            openSign(REQ_SIGN_SELLER);
        } else if (id == R.id.btnSignBuyer) {
            openSign(REQ_SIGN_BUYER);
        }
    }

    private void save(boolean showResult) {
        if (model == null) return;
        bindFromForm();

        new Thread(() -> {
            final BM7501Repository.WriteResult result = repository.savePayload(model);
            runOnUiThread(() -> {
                if (result == BM7501Repository.WriteResult.CONFLICT) {
                    // Có bản ghi mới hơn: đọc lại, tuyệt đối không ghi đè.
                    showErrorMessage(R.string.bm7501_conflict);
                    loadOrCreate();
                    return;
                }
                if (result == BM7501Repository.WriteResult.INVALID_STATE) {
                    showErrorMessage(R.string.bm7501_locked);
                    return;
                }
                if (showResult) {
                    showValidationSummary();
                }
            });
        }, "BM7501-Save").start();
    }

    /** Cho nhân viên biết còn thiếu gì, nhưng không chặn việc lưu nháp. */
    private void showValidationSummary() {
        List<BM7501Validator.Finding> findings = BM7501Validator.validateForSigning(model);
        List<BM7501Validator.Finding> errors = BM7501Validator.errorsOnly(findings);

        if (errors.isEmpty()) {
            showInfoMessage(R.string.bm7501_saved_complete);
            return;
        }

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (BM7501Validator.Finding f : errors) {
            if (shown++ >= 8) {
                sb.append("\n… và ").append(errors.size() - 8).append(" mục khác");
                break;
            }
            sb.append("\n• ").append(f.getMessage());
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.bm7501_saved_incomplete)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void printForm() {
        if (model == null) return;
        if (!BuildConfig.THERMAL_PRINTER) {
            showInfoMessage(R.string.printer_nhiet);
            return;
        }
        bindFromForm();

        // Chưa có luồng ký nên bản in còn đóng dấu MẪU. Khi bước ký hoàn thành,
        // cờ này lấy theo businessStatus (SIGNED trở đi mới in bản gốc).
        // Đã ký thì in bản gốc; chưa ký thì chỉ được in bản MẪU không giá trị pháp lý.
        final boolean signed = model.getBusinessStatus() == BM7501Model.BusinessStatus.SIGNED
                || model.getBusinessStatus() == BM7501Model.BusinessStatus.PRINTED;
        final BM7501Printer.Options options = new BM7501Printer.Options()
                .specimen(!signed)
                .copy(model.getBusinessStatus() == BM7501Model.BusinessStatus.PRINTED)
                .zq520(currentApp.getSetting() != null
                        && currentApp.getSetting().getThermalPrinterType()
                            == TruckModel.THERMAL_PRINTER_TYPE.ZQ520);
        if (currentUser != null && currentUser.getInvoiceName() != null) {
            options.companyName(currentUser.getInvoiceName());
        }

        if (zebra == null) {
            zebra = new ZebraWorker(this);
            zebra.setStateListener(new ZebraWorker.ZebraStateListener() {
                @Override
                public void onConnectionError() {
                    runOnUiThread(() -> {
                        closeProgressDialog();
                        showErrorMessage(R.string.print_bm7501_no_printer);
                    });
                }

                @Override
                public void onError() {
                    runOnUiThread(() -> {
                        closeProgressDialog();
                        showErrorMessage(R.string.print_bm7501_no_printer);
                    });
                }

                @Override
                public void onSuccess() {
                    runOnUiThread(() -> closeProgressDialog());
                }
            });
        }

        setProgressDialog();
        new Thread(() -> {
            if (model.isEditable()) repository.savePayload(model);
            zebra.print7501(model, options);
            if (signed) {
                // Bản gốc in một lần; các lần sau tăng số lần in để đóng dấu BẢN SAO.
                repository.markPrinted(model,
                        model.getBusinessStatus() == BM7501Model.BusinessStatus.PRINTED);
                runOnUiThread(this::updateStatusLine);
            }
        }, "BM7501-Print").start();
    }

    @Override
    protected void onPause() {
        // Nhân viên nhập giữa sân đỗ: rời màn hình lúc nào cũng phải giữ được nội dung.
        if (model != null && model.isEditable()) {
            bindFromForm();
            new Thread(() -> repository.savePayload(model), "BM7501-Autosave").start();
        }
        super.onPause();
    }

    // ------------------------------------------------------------------ chữ ký

    private static final int REQ_SIGN_SECTION_A = 7501;
    private static final int REQ_SIGN_SELLER = 7502;
    private static final int REQ_SIGN_BUYER = 7503;

    private void openSign(int requestCode) {
        if (model == null) return;
        if (!model.isEditable()) {
            showErrorMessage(R.string.bm7501_locked);
            return;
        }
        bindFromForm();
        startActivityForResult(new Intent(this, ReceiptSignActivity.class), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null
                || (requestCode != REQ_SIGN_SECTION_A && requestCode != REQ_SIGN_SELLER
                    && requestCode != REQ_SIGN_BUYER)) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        final String source = data.getStringExtra("signature_file");
        final String slot = requestCode == REQ_SIGN_SECTION_A
                ? BM7501Signatures.SLOT_CUSTOMER_SECTION_A
                : requestCode == REQ_SIGN_SELLER
                    ? BM7501Signatures.SLOT_SKYPEC
                    : BM7501Signatures.SLOT_CUSTOMER_FINAL;

        new Thread(() -> {
            BM7501Signatures.Stored stored = null;
            try {
                // Ảnh ký nằm ở bộ nhớ ngoài dạng file tạm; chép ngay vào vùng riêng của app
                // kèm checksum, vì đây là chứng từ pháp lý.
                stored = BM7501Signatures.store(this, model.getUniqueId(), slot, source);
            } catch (Exception ex) {
                Logger.appendLog(LOG_TAG, "Lưu chữ ký lỗi: " + ex.getMessage());
            }

            final BM7501Signatures.Stored result = stored;
            runOnUiThread(() -> {
                if (result == null) {
                    showErrorMessage(R.string.bm7501_sign_save_failed);
                    return;
                }
                if (BM7501Signatures.SLOT_CUSTOMER_SECTION_A.equals(slot)) {
                    model.setCustomerSectionASignaturePath(result.getPath());
                    model.setCustomerSectionASignatureSha256(result.getSha256());
                } else if (BM7501Signatures.SLOT_SKYPEC.equals(slot)) {
                    model.setSkypecSignaturePath(result.getPath());
                    model.setSkypecSignatureSha256(result.getSha256());
                } else {
                    model.setCustomerFinalSignaturePath(result.getPath());
                    model.setCustomerFinalSignatureSha256(result.getSha256());
                }
                updateSignatureLabels();
                new Thread(() -> repository.savePayload(model), "BM7501-SaveSign").start();
            });
        }, "BM7501-StoreSign").start();
    }

    private void updateSignatureLabels() {
        setSignLabel(R.id.lbl_sign_sectionA, "Khách hàng - mục A",
                model.getCustomerSectionASignaturePath());
        setSignLabel(R.id.lbl_sign_seller, "Người bán (SKYPEC)",
                model.getSkypecSignaturePath());
        setSignLabel(R.id.lbl_sign_buyer, "Người mua (khách hàng)",
                model.getCustomerFinalSignaturePath());
    }

    private void setSignLabel(int id, String label, String path) {
        boolean signed = path != null && !path.isEmpty();
        ((TextView) findViewById(id)).setText(label + (signed ? ": đã ký" : ": chưa ký"));
    }

    /**
     * Ký và hoàn tất phiếu: kiểm tra đủ thông tin, đẩy trạng thái tới C_DONE rồi ký.
     * Sau khi ký phiếu bị khoá, và bản in sẽ là bản gốc thay vì bản MẪU.
     */
    private void signAndFinish() {
        if (model == null) return;
        if (!model.isEditable()) {
            showErrorMessage(R.string.bm7501_locked);
            return;
        }
        bindFromForm();

        List<BM7501Validator.Finding> errors =
                BM7501Validator.errorsOnly(BM7501Validator.validateForSigning(model));
        if (!errors.isEmpty()) {
            showValidationSummary();
            return;
        }
        if (model.getLocalNumber() == null || model.getLocalNumber().isEmpty()) {
            // Số phiếu lấy từ số phiếu của mẻ hút; chưa xuất phiếu thì chưa có số để in.
            showErrorMessage(R.string.bm7501_no_number);
            return;
        }

        showConfirmMessage(R.string.bm7501_confirm_sign, () -> {
            doSign();
            return null;
        });
    }

    private void doSign() {
        setProgressDialog();
        new Thread(() -> {
            BM7501Repository.WriteResult result = repository.savePayload(model);

            // Ký chỉ đi được từ C_DONE, nên đẩy tuần tự qua các bước đã đủ dữ liệu.
            if (result != BM7501Repository.WriteResult.CONFLICT) {
                repository.advanceStep(model, BM7501Model.BusinessStatus.A_DONE);
                repository.advanceStep(model, BM7501Model.BusinessStatus.B_DONE);
                repository.advanceStep(model, BM7501Model.BusinessStatus.C_DONE);
                result = repository.sign(model);
            }

            final BM7501Repository.WriteResult finalResult = result;
            runOnUiThread(() -> {
                closeProgressDialog();
                if (finalResult == BM7501Repository.WriteResult.OK) {
                    showInfoMessage(R.string.bm7501_signed_ok);
                    updateStatusLine();
                    setFormEnabled(false);
                } else if (finalResult == BM7501Repository.WriteResult.CONFLICT) {
                    showErrorMessage(R.string.bm7501_conflict);
                    loadOrCreate();
                } else {
                    showErrorMessage(R.string.bm7501_sign_blocked);
                }
            });
        }, "BM7501-Sign").start();
    }

    /** Khoá form sau khi ký — chứng từ đã ký không được sửa nội dung. */
    private void setFormEnabled(boolean enabled) {
        findViewById(R.id.btnSave7501).setEnabled(enabled);
        findViewById(R.id.btnSign7501).setEnabled(enabled);
        findViewById(R.id.btnSignSectionA).setEnabled(enabled);
        findViewById(R.id.btnSignSeller).setEnabled(enabled);
        findViewById(R.id.btnSignBuyer).setEnabled(enabled);
    }

    // ------------------------------------------------------------------ tiện ích view

    private void setText(int id, String value) {
        ((EditText) findViewById(id)).setText(value == null ? "" : value);
    }

    private String fieldText(int id) {
        String s = ((EditText) findViewById(id)).getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void setNumber(int id, Double value) {
        ((EditText) findViewById(id)).setText(
                value == null ? "" : String.format(Locale.US, "%s", trimZero(value)));
    }

    private static String trimZero(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private Double getNumber(int id) {
        String s = fieldText(id);
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void setChecked(int id, boolean checked) {
        ((CheckBox) findViewById(id)).setChecked(checked);
    }

    private boolean isChecked(int id) {
        return ((CheckBox) findViewById(id)).isChecked();
    }

    private void checkRadio(int groupId, int buttonId) {
        ((RadioGroup) findViewById(groupId)).check(buttonId);
    }

    private int checkedId(int groupId) {
        return ((RadioGroup) findViewById(groupId)).getCheckedRadioButtonId();
    }

    private void setSpinnerValue(int id, int index) {
        ((Spinner) findViewById(id)).setSelection(Math.max(index, 0));
    }

    private int selectedIndex(int id) {
        return ((Spinner) findViewById(id)).getSelectedItemPosition();
    }

    // ------------------------------------------------------------------ ánh xạ enum

    private static int kitIndex(MicrobialKit kit) {
        if (kit == null) return 0;
        switch (kit) {
            case HY_LITE: return 1;
            case MICROB_MONITOR2: return 2;
            case FUELSTAT: return 3;
            case OTHER: return 4;
            default: return 0;
        }
    }

    private static MicrobialKit kitAt(int index) {
        switch (index) {
            case 1: return MicrobialKit.HY_LITE;
            case 2: return MicrobialKit.MICROB_MONITOR2;
            case 3: return MicrobialKit.FUELSTAT;
            case 4: return MicrobialKit.OTHER;
            default: return null;
        }
    }

    private static int resultIndex(MicrobialResult r) {
        if (r == null) return 0;
        switch (r) {
            case NORMAL: return 1;
            case WARNING: return 2;
            case ACTION: return 3;
            default: return 0;
        }
    }

    private static MicrobialResult resultAt(int index) {
        switch (index) {
            case 1: return MicrobialResult.NORMAL;
            case 2: return MicrobialResult.WARNING;
            case 3: return MicrobialResult.ACTION;
            default: return null;
        }
    }

    private static int handlingIndex(HandlingOption h) {
        if (h == null) return 0;
        switch (h) {
            case STORAGE: return 1;
            case SAME_AIRCRAFT: return 2;
            case OTHER_AIRCRAFT_SAME_AIRLINE: return 3;
            case AUTHORIZE_SKYPEC: return 4;
            case REFUEL_DESPITE_ISSUE: return 5;
            default: return 0;
        }
    }

    private static HandlingOption handlingAt(int index) {
        switch (index) {
            case 1: return HandlingOption.STORAGE;
            case 2: return HandlingOption.SAME_AIRCRAFT;
            case 3: return HandlingOption.OTHER_AIRCRAFT_SAME_AIRLINE;
            case 4: return HandlingOption.AUTHORIZE_SKYPEC;
            case 5: return HandlingOption.REFUEL_DESPITE_ISSUE;
            default: return null;
        }
    }

    private static String formatDate(Date d) {
        return d == null ? "" : new SimpleDateFormat(TIME_PATTERN, Locale.US).format(d);
    }

    private static Date parseDate(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return new SimpleDateFormat(TIME_PATTERN, Locale.US).parse(s.trim());
        } catch (ParseException ex) {
            return null;
        }
    }
}
