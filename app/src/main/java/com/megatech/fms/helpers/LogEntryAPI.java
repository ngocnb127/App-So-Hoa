package com.megatech.fms.helpers;

import com.megatech.fms.BuildConfig;
import com.megatech.fms.FMSApplication;
import com.megatech.fms.model.LogEntryModel;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogEntryAPI extends BaseAPI{

    public boolean postLogs(List<LogEntryModel> models)
    {
        String url = BuildConfig.API_BASE_URL + "api/log/list";
        String params = gson.toJson(models);
        try {
            HttpResponse response =  httpClient.sendPOST(url, params);
            return  response.getResponseCode() == HttpURLConnection.HTTP_OK;
        }
        catch (Exception ex)
        {

        }
        return  false;
    }

    private File zipLogFile(String filePath) throws IOException {
        File src = new File(filePath);
        if (!src.exists() || !src.isFile()) {
            throw new IOException("Log file not found: " + filePath);
        }

        // Tên file .zip: <tên-gốc>.zip, ví dụ: app.fms.log.zip
        File zipFile = new File(src.getParentFile(), src.getName() + ".zip");

        byte[] buffer = new byte[8192];
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
             BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src))) {

            ZipEntry zipEntry = new ZipEntry(src.getName()); // tên entry bên trong zip
            zos.putNextEntry(zipEntry);

            int len;
            while ((len = bis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }

            zos.closeEntry();
        }

        return zipFile;
    }

    public boolean postLogFile(String filePath) throws IOException {

        String url = BuildConfig.API_BASE_URL + "api/log2";

        String boundary = "*****" + System.currentTimeMillis() + "*****";
        String CRLF = "\r\n";
        String charset = "UTF-8";
        String truckNo = FMSApplication.getApplication().getTruckNo();
        String tabletId = FMSApplication.getApplication().getTabletId();

        // Nén file trước khi gửi
        File zipped = null;
        HttpURLConnection con = null;
        try {
            zipped = zipLogFile(filePath);

            // Gợi ý tên file tải lên: <truckNo>-<yyyyMMdd_HHmmss>.fms.log.zip
            String timeSuffix = new SimpleDateFormat("yyyyMMdd").format(new Date());
            String uploadFileName = truckNo + "-" + timeSuffix + "---" + tabletId + "--.fms.log.zip";

            con = httpClient.createConnection(url, "POST", "multipart/form-data; boundary=" + boundary);

            try (OutputStream output = con.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true)) {

                // Phần header của part file
                writer.append("--").append(boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"textFile\"; filename=\"")
                        .append(uploadFileName).append("\"").append(CRLF);

                // Nếu backend chấp nhận application/zip, dùng Content-Type này.
                // Nếu backend yêu cầu text/plain dù là zip, có thể đổi lại "text/plain".
                writer.append("Content-Type: application/zip").append(CRLF);
                writer.append(CRLF).flush();

                // Ghi nội dung file zip
                Files.copy(zipped.toPath(), output);
                output.flush();

                // Kết thúc part
                writer.append(CRLF).flush();
                // Kết thúc multipart
                writer.append("--").append(boundary).append("--").append(CRLF).flush();
            }

            int responseCode = con.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (Exception ex) {
            // Bạn có thể ghi log chi tiết tại đây nếu cần
            return false;

        } finally {
            if (con != null) {
                con.disconnect();
            }
            // Xoá file zip tạm sau khi upload xong
            if (zipped != null && zipped.exists()) {
                try { zipped.delete(); } catch (Exception ignore) {}
            }
        }
    }
}
