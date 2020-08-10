package gradlebuild;

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Internal;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public abstract class BintrayTask extends DefaultTask {
    private String userName;
    private String apiKey;

    @Internal
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Internal
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    void put(URI uploadUrl, long length, InputStream instr) throws Exception {
        doRequest(new HttpPut(uploadUrl), length, instr);
    }

    void post(URI uploadUrl, long length, InputStream instr) throws Exception {
        doRequest(new HttpPost(uploadUrl), length, instr);
    }

    void patch(URI uploadUrl, long length, InputStream instr) throws Exception {
        doRequest(new HttpPatch(uploadUrl), length, instr);
    }

    private void doRequest(HttpUriRequestBase request, long length, InputStream instr) throws Exception {
        SSLContext sslcontext = SSLContextBuilder.create().setProtocol("TLSv1.2").build();
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register("https", new SSLConnectionSocketFactory(sslcontext))
                .build();
        CloseableHttpClient client = HttpClients.custom()
                .setConnectionManager(new BasicHttpClientConnectionManager(socketFactoryRegistry))
                .build();
        try {
            BasicScheme basicAuth = new BasicScheme();
            basicAuth.initPreemptive(new UsernamePasswordCredentials(userName, apiKey.toCharArray()));
            HttpClientContext localContext = HttpClientContext.create();
            localContext.resetAuthExchange(new HttpHost("https", request.getUri().getHost(), 443), basicAuth);
            request.setEntity(new InputStreamEntity(instr, length, null));
            CloseableHttpResponse response = client.execute(request, localContext);
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                copyTo(response.getEntity().getContent(), outputStream);
                if (response.getCode() != 200 && response.getCode() != 201) {
                    System.out.println(new String(outputStream.toByteArray()));
                    throw new HttpException(response.getCode(), "Received " + response.getCode() + " for " + request.getMethod() + " " + request.getUri());
                }
            } finally {
                response.close();
            }
        } finally {
            client.close();
        }
    }

    private void copyTo(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[1024 * 16];
        while (true) {
            int nread = inputStream.read(buffer);
            if (nread < 0) {
                break;
            }
            outputStream.write(buffer, 0, nread);
        }
    }

    static class HttpException extends IOException {
        private final int statusCode;

        public HttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
