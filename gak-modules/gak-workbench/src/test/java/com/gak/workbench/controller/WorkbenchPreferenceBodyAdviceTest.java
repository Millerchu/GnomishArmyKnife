package com.gak.workbench.controller;

import com.gak.workbench.dto.WorkbenchPreferenceRequest;
import com.gak.workbench.exception.WorkbenchPreferenceInvalidException;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import static org.junit.jupiter.api.Assertions.*;

/** 无长度的流也必须有界读取，不能只相信 Content-Length。 */
class WorkbenchPreferenceBodyAdviceTest {
    private final WorkbenchPreferenceBodyAdvice advice = new WorkbenchPreferenceBodyAdvice();

    @Test
    void limitsStreamingReadAndAllowsExactRawLimit() throws Exception {
        byte[] huge = new byte[1024 * 1024];
        var stream = new ByteArrayInputStream(huge);
        var message = message(stream);
        assertThrows(WorkbenchPreferenceInvalidException.class,
                () -> advice.beforeBodyRead(message, null, WorkbenchPreferenceRequest.class,
                        MappingJackson2HttpMessageConverter.class));
        assertTrue(huge.length - stream.available() <= 65537);
        var accepted = advice.beforeBodyRead(message(new ByteArrayInputStream(new byte[65536])),
                null, WorkbenchPreferenceRequest.class, MappingJackson2HttpMessageConverter.class);
        assertEquals(65536, accepted.getBody().readAllBytes().length);
    }

    private HttpInputMessage message(ByteArrayInputStream stream) {
        return new HttpInputMessage() {
            @Override
            public ByteArrayInputStream getBody() { return stream; }
            @Override
            public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
    }
}
