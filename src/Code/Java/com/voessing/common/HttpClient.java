package com.voessing.common;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;

/**
 * The HttpClient class provides methods for making HTTP requests using Apache HttpClient.
 */
public class HttpClient {

    private final CloseableHttpClient httpClient;

    private final HttpClientContext context;

    private Header authHeader;
    private List<Header> defaultHeaders;

    /**
     * Represents the response of an HTTP request.
     */
    public static class Response {
        private boolean ok;
        private int status;
        private String statusText;
        private String content;
        private String contentEncoding;
        private String contentType;
        private long contentLength;
        private Map<String, String> headers;

        /**
         * Parses the response content as JSON and returns the corresponding object.
         * 
         * @return The parsed JSON object, or null if the content is not valid JSON. Either a {@link JsonJavaObject} or a {@link JsonJavaArray}.
         */
        public <T extends Object> T parseWithHCL() {
            try {
                return (T) JsonParser.fromJson(JsonJavaFactory.instanceEx, this.content);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Parses the content of the HTTP response using GSON and returns it as a
         * JsonElement or its subclass.
         * If the content cannot be parsed, this method returns null.
         *
         * @return the parsed content as a JsonElement or its subclass, or null if the
         *         content cannot be parsed
         */
        public <T extends JsonElement> T parseWithGSON() {
            try {
                return (T) com.google.gson.JsonParser.parseString(this.content);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Maps the content of the HTTP response to an object of the specified class
         * using GSON.
         *
         * @param clazz the class of the object to return
         * @return the content of the HTTP response mapped to an object of the specified
         *         class
         */
        public <T> T mapJsonWithGSON(Class<T> clazz) {
            return new Gson().fromJson(this.content, clazz);
        }

        /**
         * Checks if the request was successful.
         * 
         * @return true if the request was successful, false otherwise.
         */
        public boolean isOk() {
            return ok;
        }

        /**
         * Sets the flag indicating if the request was successful.
         * 
         * @param ok The flag indicating if the request was successful.
         */
        public void setOk(boolean ok) {
            this.ok = ok;
        }

        /**
         * Gets the status text of the response.
         * 
         * @return The status text of the response.
         */
        public String getStatusText() {
            return statusText;
        }

        /**
         * Sets the status text of the response.
         * 
         * @param statusText The status text of the response.
         */
        public void setStatusText(String statusText) {
            this.statusText = statusText;
        }

        /**
         * Gets the status code of the response.
         * 
         * @return The status code of the response.
         */
        public int getStatus() {
            return status;
        }

        /**
         * Sets the status code of the response.
         * 
         * @param statusCode The status code of the response.
         */
        public void setStatus(int statusCode) {
            this.status = statusCode;
        }

        /**
         * Gets the content of the response.
         * 
         * @return The content of the response.
         */
        public String getContent() {
            return content;
        }

        /**
         * Sets the content of the response.
         * 
         * @param content The content of the response.
         */
        public void setContent(String content) {
            this.content = content;
        }

        /**
         * Gets the content encoding of the response.
         * 
         * @return The content encoding of the response.
         */
        public String getContentEncoding() {
            return contentEncoding;
        }

        /**
         * Sets the content encoding of the response.
         * 
         * @param contentEncoding The content encoding of the response.
         */
        public void setContentEncoding(String contentEncoding) {
            this.contentEncoding = contentEncoding;
        }

        /**
         * Gets the content type of the response.
         * 
         * @return The content type of the response.
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * Sets the content type of the response.
         * 
         * @param contentType The content type of the response.
         */
        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        /**
         * Gets the content length of the response.
         * 
         * @return The content length of the response.
         */
        public long getContentLength() {
            return contentLength;
        }

        /**
         * Sets the content length of the response.
         * 
         * @param contentLength The content length of the response.
         */
        public void setContentLength(long contentLength) {
            this.contentLength = contentLength;
        }

        /**
         * Gets the headers of the response.
         * 
         * @return The headers of the response.
         */
        public Map<String, String> getHeaders() {
            return headers;
        }

        /**
         * Sets the headers of the response.
         * 
         * @param headers The headers of the response.
         */
        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
    }

    /**
     * Constructs a new HttpClient instance.
     */
    public HttpClient() {
        this.httpClient = HttpClients.createDefault();
        this.context = null;
    }

    /**
     * Constructs a new instance of the HttpClient class.
     *
     * @param useContext a boolean value indicating whether to use a context for the HttpClient
     */
    public HttpClient(boolean useContext){
        this.httpClient = HttpClients.createDefault();
        if(useContext){
            this.context = HttpClientContext.create();
        } else {
            this.context = null;
        }
    }

    /**
     * Sets the Authorization header to use a Bearer token.
     * Any existing Authorization header is replaced.
     *
     * @param token the Bearer token to be used for authorization
     */
    public void useBearerToken(String token) {
        this.authHeader = new BasicHeader("Authorization", "Bearer " + token);
    }

    /**
     * Sets the Authorization header to use Basic authentication.
     * Any existing Authorization header is replaced.
     *
     * @param username the username for Basic authentication
     * @param password the password for Basic authentication
     */
    public void useBasicAuth(String username, String password) {
        this.authHeader = new BasicHeader("Authorization",
                "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));
    }

    /**
     * Sets the Authorization header to use OAuth.
     * Any existing Authorization header is replaced.
     *
     * @param consumerKey the consumer key for OAuth
     * @param token       the token for OAuth
     */
    public void useOAuth(String consumerKey, String token) {
        this.authHeader = new BasicHeader("Authorization",
                "OAuth oauth_consumer_key=\"" + consumerKey + "\", oauth_token=\"" + token + "\"");
    }

    /**
     * Removes the Authorization header.
     */
    public void removeAuthHeader() {
        this.authHeader = null;
    }

    /**
     * Adds a default header that will be included in all requests.
     * If the default headers list is not initialized, it will be initialized.
     *
     * @param name  the name of the header
     * @param value the value of the header
     */
    public void addDefaultHeader(String name, String value) {
        if (this.defaultHeaders == null) {
            this.defaultHeaders = new java.util.ArrayList<>();
        }
        this.defaultHeaders.add(new BasicHeader(name, value));
    }

    /**
     * Removes a default header from the list of default headers.
     * If the default headers list is not initialized, this method does nothing.
     *
     * @param name the name of the header to remove
     */
    public void removeDefaultHeader(String name) {
        if (this.defaultHeaders != null) {
            this.defaultHeaders.removeIf(header -> header.getName().equals(name));
        }
    }

    /**
     * Clears all default headers.
     * After calling this method, the list of default headers will be null.
     */
    public void clearDefaultHeaders() {
        this.defaultHeaders = null;
    }

    /**
     * Clears all cookies from the cookie store.
     */
    public void clearCookies() {
        this.context.getCookieStore().clear();
    }

    /**
     * Sets the cookies in the cookie store.
     * Any existing cookies in the cookie store are cleared before the new cookies
     * are added.
     *
     * @param cookies the list of cookies to be added to the cookie store
     */
    public void setCookies(List<Cookie> cookies) {
        this.context.getCookieStore().clear();
        cookies.forEach(cookie -> this.context.getCookieStore().addCookie(cookie));
    }

    /**
     * Adds a single cookie to the cookie store.
     *
     * @param cookie the cookie to be added to the cookie store
     */
    public void addCookie(Cookie cookie) {
        this.context.getCookieStore().addCookie(cookie);
    }

    /**
     * Retrieves all cookies from the cookie store.
     *
     * @return a list of all cookies in the cookie store
     */
    public List<Cookie> getCookies() {
        return this.context.getCookieStore().getCookies();
    }

    /**
     * Sends an HTTP GET request to the specified URL.
     * 
     * @param url The URL to send the request to.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response fetch(String url) throws IOException {
        return fetch(url, "GET", null, null);
    }

    /**
     * Sends an HTTP request to the specified URL with the specified method.
     * 
     * @param url The URL to send the request to.
     * @param method The HTTP method to use.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response fetch(String url, String method) throws IOException {
        return fetch(url, method, null, null);
    }

    /**
     * Sends an HTTP request to the specified URL with the specified method and entity.
     * 
     * @param url The URL to send the request to.
     * @param method The HTTP method to use.
     * @param entity The HTTP entity to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response fetch(String url, String method, HttpEntity entity) throws IOException {
        return fetch(url, method, entity, null);
    }

    /**
     * Sends an HTTP request to the specified URL with the specified method and headers.
     * 
     * @param url The URL to send the request to.
     * @param method The HTTP method to use.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response fetch(String url, String method, List<Header> headers) throws IOException {
        return fetch(url, method, null, headers);
    }

    /**
     * Sends an HTTP request to the specified URL with the specified method, entity, and headers.
     * 
     * @param url The URL to send the request to.
     * @param method The HTTP method to use.
     * @param entity The HTTP entity to send with the request.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response fetch(String url, String method, HttpEntity entity, List<Header> headers) throws IOException {
        switch (method) {
            case "GET":
                return get(url, headers);
            case "POST":
                return post(url, entity, headers);
            case "PUT":
                return put(url, entity, headers);
            case "PATCH":
                return patch(url, entity, headers);
            case "DELETE":
                return delete(url, headers);
            default:
                throw new IllegalArgumentException("Method not supported");
        }
    }

    /**
     * Sends an HTTP GET request to the specified URL.
     * 
     * @param url The URL to send the request to.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response get(String url) throws IOException {
        return get(url, null);
    }

    /**
     * Sends an HTTP GET request to the specified URL with the specified headers.
     * 
     * @param url The URL to send the request to.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response get(String url, List<Header> headers) throws IOException {
        return get(url, null, headers);
    }

    /**
     * Sends an HTTP GET request to the specified URL with the specified entity and headers.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response get(String url, HttpEntity entity, List<Header> headers) throws IOException {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setEntity(entity);
        return executeRequest(httpGet, headers);
    }

    /**
     * Sends an HTTP POST request to the specified URL with the specified entity.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response post(String url, HttpEntity entity) throws IOException {
        return post(url, entity, null);
    }

    /**
     * Sends an HTTP POST request to the specified URL with the specified entity and headers.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response post(String url, HttpEntity entity, List<Header> headers) throws IOException {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(entity);
        return executeRequest(httpPost, headers);
    }

    /**
     * Sends an HTTP PUT request to the specified URL with the specified entity.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response put(String url, HttpEntity entity) throws IOException {
        return put(url, entity, null);
    }

    /**
     * Sends an HTTP PUT request to the specified URL with the specified entity and headers.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response put(String url, HttpEntity entity, List<Header> headers) throws IOException {
        HttpPut httpPut = new HttpPut(url);
        httpPut.setEntity(entity);
        return executeRequest(httpPut, headers);
    }

    /**
     * Sends an HTTP PATCH request to the specified URL with the specified entity.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response patch(String url, HttpEntity entity) throws IOException {
        return patch(url, entity, null);
    }

    /**
     * Sends an HTTP PATCH request to the specified URL with the specified entity and headers.
     * 
     * @param url The URL to send the request to.
     * @param entity The HTTP entity to send with the request.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response patch(String url, HttpEntity entity, List<Header> headers) throws IOException {
        HttpPatch httpPatch = new HttpPatch(url);
        httpPatch.setEntity(entity);
        return executeRequest(httpPatch, headers);
    }

    /**
     * Sends an HTTP DELETE request to the specified URL.
     * 
     * @param url The URL to send the request to.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response delete(String url) throws IOException {
        return delete(url, null);
    }

    /**
     * Sends an HTTP DELETE request to the specified URL with the specified headers.
     * 
     * @param url The URL to send the request to.
     * @param headers The headers to send with the request.
     * @return The response of the request.
     * @throws IOException If an I/O error occurs.
     */
    public Response delete(String url, List<Header> headers) throws IOException {
        HttpDelete httpDelete = new HttpDelete(url);
        return executeRequest(httpDelete, headers);
    }

    private Response executeRequest(HttpUriRequestBase request, List<Header> headers) throws IOException {

        if (authHeader != null) {
            request.addHeader(authHeader);
        }

        if (defaultHeaders != null) {
            defaultHeaders.forEach(request::addHeader);
        }

        if (headers != null) {
            headers.forEach(request::addHeader);
        }

        return httpClient.execute(request, context, this::handleResponse);
    }

    private Response handleResponse(ClassicHttpResponse response) throws IOException, ParseException {
        Response result = new Response();

        // Statuscode handling
        int statusCode = response.getCode();
        result.setStatus(statusCode);
        result.setOk(statusCode >= 200 && statusCode < 300);
        result.setStatusText(response.getReasonPhrase());

        // Header handling
        result.setHeaders(getHeaders(response));

        // Content handling
        HttpEntity entity = response.getEntity();
        result.setContentLength(entity.getContentLength());
        result.setContentType(entity.getContentType());
        result.setContentEncoding(entity.getContentEncoding());
        result.setContent(EntityUtils.toString(entity));

        

        return result;
    }

    private Map<String, String> getHeaders(ClassicHttpResponse response) {
        return Arrays.stream(response.getHeaders())
                .collect(Collectors.toMap(Header::getName, Header::getValue));
    }
}
