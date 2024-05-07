package com.voessing.common.http;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicHeader;

import com.google.gson.JsonElement;
import com.ibm.commons.util.io.json.JsonJavaArray;
import com.ibm.commons.util.io.json.JsonJavaObject;

public class HttpUtil {

    public static class FileData {
        private byte[] content;
        private String fileName;
        private ContentType contentType;

        public FileData(byte[] content, String fileName, ContentType contentType) {
            this.content = content;
            this.fileName = fileName;
            this.contentType = contentType;
        }

        public FileData() {
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public ContentType getContentType() {
            return contentType;
        }

        public void setContentType(ContentType contentType) {
            this.contentType = contentType;
        }

    }

    /**
     * Converts a list of headers to a map.
     *
     * @param headers the list of headers
     * @return a map where the keys are the header names and the values are the
     *         header values
     */
    public static Map<String, String> headerListToMap(List<Header> headers) {
        return headers.stream().collect(Collectors.toMap(Header::getName, Header::getValue));
    }

    /**
     * Converts a map of headers to a list.
     *
     * @param headers the map of headers
     * @return a list of headers
     */
    public static List<Header> headerMapToList(Map<String, String> headers) {
        return headers.entrySet().stream().map(entry -> new BasicHeader(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * Creates an HTTP entity with the specified JSON element and content type.
     *
     * @param json the JSON element
     * @return the HTTP entity
     */
    public static HttpEntity createEntity(JsonElement json) {
        return createEntity(ContentType.APPLICATION_JSON, json.toString());
    }

    /**
     * Creates an HTTP entity with the specified JSON array and content type.
     *
     * @param json the JSON array
     * @return the HTTP entity
     */
    public static HttpEntity createEntity(JsonJavaArray json) {
        return createEntity(ContentType.APPLICATION_JSON, json.toString());
    }

    /**
     * Creates an HTTP entity with the specified JSON object and content type.
     *
     * @param json the JSON object
     * @return the HTTP entity
     */
    public static HttpEntity createEntity(JsonJavaObject json) {
        return createEntity(ContentType.APPLICATION_JSON, json.toString());
    }

    /**
     * Creates an HTTP entity with the specified content type and content.
     *
     * @param contentType the content type
     * @param content     the content
     * @return the HTTP entity
     */
    public static HttpEntity createEntity(ContentType contentType, String content) {
        return EntityBuilder
                .create()
                .setText(content)
                .setContentType(contentType.withCharset(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Creates a multipart HTTP entity with the specified fields and files.
     *
     * @param fields the fields
     * @param files  the files
     * @return the multipart HTTP entity
     */
    public static HttpEntity createMultipartEntity(Map<String, String> fields, Map<String, FileData> files) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .setContentType(ContentType.MULTIPART_FORM_DATA);

        if (fields != null) {
            fields.forEach(builder::addTextBody);
        }

        if (files != null) {
            files.forEach((name, fileData) -> builder.addBinaryBody(name, fileData.getContent(),
                    fileData.getContentType(), fileData.getFileName()));
        }

        return builder.build();
    }
}
