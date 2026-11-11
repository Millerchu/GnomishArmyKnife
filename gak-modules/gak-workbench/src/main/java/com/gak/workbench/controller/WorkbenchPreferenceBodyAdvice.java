package com.gak.workbench.controller;

import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** 在 Jackson 分配 JSON 树之前限制原始字节，同时覆盖无 Content-Length 的分块请求。 */
@ControllerAdvice(assignableTypes = WorkbenchPreferenceController.class)
public class WorkbenchPreferenceBodyAdvice extends RequestBodyAdviceAdapter {
    public static final int MAX_REQUEST_BYTES = 65536;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == WorkbenchPreferenceRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (message.getHeaders().getContentLength() > MAX_REQUEST_BYTES) {
            throw new WorkbenchPreferenceInvalidException("请求体不能超过 65536 字节");
        }
        byte[] bytes = message.getBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new WorkbenchPreferenceInvalidException("请求体不能超过 65536 字节");
        }
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return message.getHeaders();
            }
        };
    }
}
