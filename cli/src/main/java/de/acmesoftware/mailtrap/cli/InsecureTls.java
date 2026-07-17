package de.acmesoftware.mailtrap.cli;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Builds the HTTP client. With {@code --insecure} (or a context's {@code insecureTls}) certificate
 * verification is switched off — for a dev trap behind a self-signed certificate. Never a default:
 * the flag has to be asked for.
 */
final class InsecureTls {

    private InsecureTls() {
    }

    static HttpClient.Builder builder(boolean insecure) {
        HttpClient.Builder b = HttpClient.newBuilder();
        if (insecure) {
            b.sslContext(trustAll());
        }
        return b;
    }

    private static SSLContext trustAll() {
        try {
            TrustManager[] tm = {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tm, new SecureRandom());
            // The JDK client also verifies the hostname unless this is off.
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            return ctx;
        } catch (Exception e) {
            throw new CliError("Cannot disable TLS verification: " + e.getMessage());
        }
    }
}
