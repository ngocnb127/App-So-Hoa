package com.megatech.fms.rut;

import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * TLS cho router có chứng thư TỰ KÝ, không dùng trust-all.
 *
 * <p>Router RUT xuất xưởng với chứng thư do chính nó ký, nên không CA nào chứng thực và hệ
 * thống tin cậy của Android từ chối. Cách hay bị dùng — trust manager chấp nhận mọi chứng
 * thư — biến mọi máy chủ trong LAN thành router hợp lệ, kể cả một máy tính cắm nhờ vào
 * cùng mạng.
 *
 * <p>Ở đây dùng GHIM VÂN TAY: lần đầu nhận vân tay SHA-256 của chứng thư router và nhớ lại
 * ({@link RutConfigStore#rememberCertificate}); từ lần sau chỉ chấp nhận đúng vân tay đó.
 * Đây là mô hình "tin ở lần gặp đầu" — chấp nhận rủi ro đúng một lần, trong LAN của chính
 * chiếc xe, đổi lại mọi lần sau đều được xác thực thật.
 */
public final class RutTls {

    /** Vân tay của chứng thư vừa bắt tay, để lớp gọi ghim lại sau lần đầu. */
    public interface CertificateObserver {
        void onLeafCertificate(String sha256Hex);
    }

    private RutTls() {
    }

    public static SSLSocketFactory socketFactory(@Nullable String pinnedSha256Hex,
                                                 CertificateObserver observer) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new javax.net.ssl.TrustManager[]{
                new PinningTrustManager(pinnedSha256Hex, observer)}, null);
        return context.getSocketFactory();
    }

    public static X509TrustManager trustManager(@Nullable String pinnedSha256Hex,
                                                CertificateObserver observer) {
        return new PinningTrustManager(pinnedSha256Hex, observer);
    }

    private static final class PinningTrustManager implements X509TrustManager {
        @Nullable
        private final String pinned;
        private final CertificateObserver observer;

        PinningTrustManager(@Nullable String pinned, CertificateObserver observer) {
            this.pinned = pinned;
            this.observer = observer;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException("Client không bao giờ đóng vai server");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0)
                throw new CertificateException("Router không gửi chứng thư");

            String fingerprint = sha256(chain[0]);

            if (pinned == null) {
                // Lần gặp đầu: nhận và ghim. Chỉ xảy ra một lần cho mỗi router.
                observer.onLeafCertificate(fingerprint);
                return;
            }
            if (!pinned.equalsIgnoreCase(fingerprint))
                throw new CertificateException(
                        "Chứng thư router đã đổi so với lần ghép cặp");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /** Vân tay SHA-256 của chứng thư, dạng hex thường. */
    public static String sha256(X509Certificate certificate) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(certificate.getEncoded());
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new CertificateException("Không tính được vân tay chứng thư", ex);
        }
    }
}
