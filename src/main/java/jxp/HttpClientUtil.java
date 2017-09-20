package jxp;

import okhttp3.*;
import okio.Buffer;
import okio.BufferedSink;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HttpClient工具类
 * ==============================
 * v 1.0.0
 * 2017年7月31日
 * 支持简单的GET、POST操作
 * 有同步与异步两种模式，异步需要实现CustomCallback接口
 */
public class HttpClientUtil {

    /**
     * 自定义异步回调接口
     */
    public interface CustomCallback {

        /**
         * 回复数据处理
         * @param url
         * @param method
         * @param headerMap
         * @param reqBody
         * @param resp
         */
        void onResponse(String url, String method, Map<String, List<String>> headerMap, byte[] reqBody, CustomResponse resp);

        /**
         * 异常处理
         * @param url
         * @param method
         * @param headerMap
         * @param reqBody
         * @param e
         */
        void onException(String url, String method, Map<String, List<String>> headerMap, byte[] reqBody, Exception e);
    }

    /**
     * 自定义回复类
     */
    public static class CustomResponse {
        private int code;
        private byte[] body;

        public int getCode() {return code;}

        public byte[] getBodyAsBytes() {return body;}

        public String getBodyAsString() {return new String(body, StandardCharsets.UTF_8);}

        public String getBodyAsString(Charset charset) {return new String(body, charset);}

        public void setCode(int code) {this.code = code;}

        public void setBody(byte[] body) {this.body = body;}
    }

    public static final String GET = "GET";
    public static final String POST = "POST";

    private static final OkHttpClient client = new OkHttpClient.Builder().build();  ///< OkHttpClient对象

    /**
     * 获取请求对象
     * @param url
     * @param method
     * @param headers
     * @param body
     * @return Request
     */
    private static Request getRequest(String url, String method, Headers headers, RequestBody body) {
        Request.Builder reqBuilder = new Request.Builder();
        //添加url
        reqBuilder.url(url);
        //添加headers
        if (headers != null && headers.size() > 0) {
            reqBuilder.headers(headers);
        }
        //添加method和requestBody
        if (GET.equals(method)) {
            //如为GET，设置body为null
            reqBuilder.method(method, null);
        } else {
            reqBuilder.method(method, body);
        }
        return reqBuilder.build();
    }

    /**
     * Map -> Headers
     * @return Headers
     */
    private static Headers getHeaders(Map<String, List<String>> headerMap) {
        Headers.Builder builder = new Headers.Builder();
        if (headerMap != null && headerMap.size() > 0) {
            for (Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
                String key = entry.getKey();
                for (String value : entry.getValue()) {
                    builder.add(key, value);
                }
            }
        }
        return builder.build();
    }

    /**
     * response -> customResponse
     * @param response
     * @return
     */
    private static CustomResponse getCustomResponse(Response response) throws IOException {
        CustomResponse customResponse = new CustomResponse();
        customResponse.setCode(response.code());
        customResponse.setBody(response.body().bytes());
        return customResponse;
    }

    /**
     * CustomCallback -> Callback
     * @param customCallback
     * @return Callback
     */
    private static Callback getCallback(final CustomCallback customCallback) {
        return new Callback() {

            private byte[] getRequestBodyBytes(Request req) {
                RequestBody reqBody = req.body();

                if (reqBody != null) {
                    BufferedSink reqBuf = new Buffer();
                    try {
                        reqBody.writeTo(reqBuf);
                        return reqBuf.buffer().readByteArray();
                    } catch (IOException ignore) {
                    }
                }
                return null;
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Request req = call.request();
                customCallback.onException(req.url().toString(), req.method(), req.headers().toMultimap(), getRequestBodyBytes(req), e);
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                Request req = call.request();
                customCallback.onResponse(req.url().toString(), req.method(), req.headers().toMultimap(), getRequestBodyBytes(req), getCustomResponse(resp));
            }
        };
    }

    /**
     * 通用同步请求
     * @param url
     * @param method
     * @param headers
     * @param body
     * @return okhttp3.CustomResponse
     * @throws IOException
     */
    private static Response requestSync(String url, String method, Headers headers, RequestBody body) throws IOException {
        Request req = getRequest(url, method, headers, body);
        return client.newCall(req).execute();
    }

    /**
     * 通用异步请求
     * @param url
     * @param method
     * @param headers
     * @param body
     * @param callback 回调如果传入null,则生成默认的回调，对回复和异常均不进行处理
     */
    private static void requestAsync(String url, String method, Headers headers, RequestBody body, Callback callback) {
        Request req = getRequest(url, method, headers, body);
        if (callback != null) {
            client.newCall(req).enqueue(callback);
        } else {
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                }
            });
        }
    }

    //==========GET==========
    /**
     * GET同步
     * @param url
     * @return CustomResponse
     * @throws IOException
     */
    public static CustomResponse getSync(String url) throws IOException {
        return getSync(url, null);
    }

    /**
     * GET同步
     * @param url
     * @param headerMap
     * @return CustomResponse
     * @throws IOException
     */
    public static CustomResponse getSync(String url, Map<String, List<String>> headerMap) throws IOException {
        Response resp = requestSync(url, GET, getHeaders(headerMap), null);
        return getCustomResponse(resp);
    }

    /**
     * GET异步
     * @param url
     * @param customCallback
     */
    public static void getAsync(String url, CustomCallback customCallback) {
        requestAsync(url, GET, null, null, getCallback(customCallback));
    }

    /**
     * GET异步
     * @param url
     * @param headerMap
     * @param customCallback
     */
    public static void getAsync(String url, Map<String, List<String>> headerMap, CustomCallback customCallback) {
        requestAsync(url, GET, getHeaders(headerMap), null, getCallback(customCallback));
    }

    //==========POST==========
    /**
     * POST同步
     * @param url
     * @param bodyBytes
     * @return CustomResponse
     * @throws IOException
     */
    public static CustomResponse postSync(String url, byte[] bodyBytes) throws IOException {
        return postSync(url, null, bodyBytes);
    }

    /**
     * POST同步
     * @param url
     * @param headerMap
     * @param bodyBytes
     * @return CustomResponse
     * @throws IOException
     */
    public static CustomResponse postSync(String url, Map<String, List<String>> headerMap, byte[] bodyBytes) throws IOException {
        Response resp = requestSync(url, POST, getHeaders(headerMap), RequestBody.create(null, bodyBytes));
        return getCustomResponse(resp);
    }

    /**
     * POST异步
     * @param url
     * @param bodyBytes
     * @param customCallback
     */
    public static void postAsync(String url, byte[] bodyBytes, CustomCallback customCallback) {
        postAsync(url, null, bodyBytes, customCallback);
    }

    /**
     * POST异步
     * @param url
     * @param headerMap
     * @param bodyBytes
     * @param customCallback
     */
    public static void postAsync(String url, Map<String, List<String>> headerMap, byte[] bodyBytes, CustomCallback customCallback) {
        requestAsync(url, POST, getHeaders(headerMap), RequestBody.create(null, bodyBytes), getCallback(customCallback));
    }

}
