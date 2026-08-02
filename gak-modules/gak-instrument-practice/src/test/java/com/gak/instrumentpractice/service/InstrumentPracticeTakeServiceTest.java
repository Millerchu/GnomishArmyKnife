package com.gak.instrumentpractice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.instrumentpractice.domain.InstrumentPracticeTake;
import com.gak.instrumentpractice.dto.PerformanceEventRequest;
import com.gak.instrumentpractice.dto.SaveInstrumentPracticeTakeRequest;
import com.gak.instrumentpractice.mapper.InstrumentPracticeTakeMapper;
import com.gak.instrumentpractice.vo.SaveInstrumentPracticeTakeResultVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 随身乐器录音容量与服务端校验测试。
 */
@ExtendWith(MockitoExtension.class)
class InstrumentPracticeTakeServiceTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private InstrumentPracticeTakeMapper instrumentPracticeTakeMapper;

    @Mock
    private UserMapper userMapper;

    private InstrumentPracticeTakeService instrumentPracticeTakeService;

    @BeforeEach
    void setUp() {
        instrumentPracticeTakeService = new InstrumentPracticeTakeService(
                instrumentPracticeTakeMapper,
                userMapper,
                new ObjectMapper()
        );
        when(userMapper.selectById(USER_ID)).thenReturn(new User());
    }

    @Test
    void createReplacesOldestTakeWhenInstrumentReachedThirtyTakes() {
        assertEquals(30, InstrumentPracticeTakeService.MAX_TAKES_PER_INSTRUMENT);
        List<InstrumentPracticeTake> existingTakes = new ArrayList<>();
        for (long id = 1; id <= InstrumentPracticeTakeService.MAX_TAKES_PER_INSTRUMENT; id++) {
            InstrumentPracticeTake take = new InstrumentPracticeTake();
            take.setId(id);
            take.setCreatedAt(LocalDateTime.of(2026, 7, 25, 10, 0).plusMinutes(id));
            existingTakes.add(take);
        }
        when(instrumentPracticeTakeMapper.selectList(any())).thenReturn(existingTakes);

        SaveInstrumentPracticeTakeResultVO result = instrumentPracticeTakeService.create(USER_ID, createValidRequest());

        assertEquals("1", result.getOverwrittenTakeId());
        verify(instrumentPracticeTakeMapper).deleteById(1L);
        ArgumentCaptor<InstrumentPracticeTake> takeCaptor = ArgumentCaptor.forClass(InstrumentPracticeTake.class);
        verify(instrumentPracticeTakeMapper).insert(takeCaptor.capture());
        assertEquals("guzheng", takeCaptor.getValue().getInstrumentId());
        assertEquals("d-pentatonic", takeCaptor.getValue().getTuningId());
    }

    @Test
    void createRejectsEventOutsideRecordedDuration() {
        SaveInstrumentPracticeTakeRequest request = createValidRequest();
        request.getEvents().getFirst().setAt(1001L);
        request.setDurationMs(1000L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> instrumentPracticeTakeService.create(USER_ID, request)
        );

        assertEquals("INSTRUMENT_EVENT_TIME_INVALID", exception.getCode());
    }

    @Test
    void createAcceptsPipaWithStandardTuning() {
        SaveInstrumentPracticeTakeRequest request = createValidRequest();
        request.setInstrumentId("pipa");
        request.setTuningId("standard-adea");
        request.getEvents().getFirst().setInstrumentId("pipa");
        when(instrumentPracticeTakeMapper.selectList(any())).thenReturn(List.of());

        instrumentPracticeTakeService.create(USER_ID, request);

        ArgumentCaptor<InstrumentPracticeTake> takeCaptor = ArgumentCaptor.forClass(InstrumentPracticeTake.class);
        verify(instrumentPracticeTakeMapper).insert(takeCaptor.capture());
        assertEquals("pipa", takeCaptor.getValue().getInstrumentId());
        assertEquals("standard-adea", takeCaptor.getValue().getTuningId());
    }

    private SaveInstrumentPracticeTakeRequest createValidRequest() {
        PerformanceEventRequest event = new PerformanceEventRequest();
        event.setAt(500L);
        event.setType("note");
        event.setInstrumentId("guzheng");
        event.setStringId("string-1");
        event.setMidi(60);
        event.setVelocity(0.7D);

        SaveInstrumentPracticeTakeRequest request = new SaveInstrumentPracticeTakeRequest();
        request.setInstrumentId("guzheng");
        request.setTuningId("d-pentatonic");
        request.setBpm(80);
        request.setMeter("4/4");
        request.setDurationMs(1000L);
        request.setEvents(List.of(event));
        return request;
    }
}
