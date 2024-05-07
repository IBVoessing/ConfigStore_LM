package com.voessing.common.http;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonJavaObject;
import com.ibm.commons.util.io.json.JsonParser;

/**
 * Represents the response of an HTTP request.
 */
public class Response {
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
