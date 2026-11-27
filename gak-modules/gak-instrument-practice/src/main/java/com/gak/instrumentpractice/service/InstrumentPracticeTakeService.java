package com.gak.instrumentpractice.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gak.framework.exception.BusinessException;
import com.gak.instrumentpractice.domain.InstrumentPracticeTake;
import com.gak.instrumentpractice.dto.PerformanceEventRequest;
import com.gak.instrumentpractice.dto.SaveInstrumentPracticeTakeRequest;
import com.gak.instrumentpractice.mapper.InstrumentPracticeTakeMapper;
import com.gak.instrumentpractice.vo.InstrumentPracticeTakeVO;
import com.gak.instrumentpractice.vo.SaveInstrumentPracticeTakeResultVO;
import com.gak.user.domain.user.User;
import com.gak.user.mapper.user.UserMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 随身乐器事件录音服务。录音只保存可回放的演奏事件，不保存麦克风或原始音频。
 */
@Service
public class InstrumentPracticeTakeService {

    public static final int MAX_TAKES_PER_INSTRUMENT = 30;
    private static final long MAX_TAKE_DURATION_MS = 10 * 60 * 1000L;
    private static final int MAX_EVENT_COUNT = 20000;
    private static final Set<String> SUPPORTED_INSTRUMENT_IDS = Set.of("guzheng", "guitar", "ukulele", "pipa", "piano");
    private static final Set<String> SUPPORTED_EVENT_TYPES = Set.of("note", "bend", "damp");
    private static final Set<String> SUPPORTED_METERS = Set.of("2/4", "3/4", "4/4", "6/8");
    private static final Map<String, Set<String>> SUPPORTED_TUNINGS = Map.of(
            "guzheng", Set.of("d-pentatonic", "g-pentatonic"),
            "guitar", Set.of("standard", "drop-d", "dadgad"),
            "ukulele", Set.of("high-g", "low-g"),
            "pipa", Set.of("standard-adea"),
            "piano", Set.of("concert-pitch")
    );
    private static final TypeReference<List<PerformanceEventRequest>> PERFORMANCE_EVENT_LIST_TYPE = new TypeReference<>() {
    };

    private final InstrumentPracticeTakeMapper instrumentPracticeTakeMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public InstrumentPracticeTakeService(InstrumentPracticeTakeMapper instrumentPracticeTakeMapper,
                                         UserMapper userMapper,
                                         ObjectMapper objectMapper) {
        this.instrumentPracticeTakeMapper = instrumentPracticeTakeMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按创建时间正序返回元数据，演奏事件由详情按需读取。
     */
    public List<InstrumentPracticeTakeVO> list(Long currentUserId) {
        ensureCurrentUserExists(currentUserId);
        return instrumentPracticeTakeMapper.selectList(ownedTakes(currentUserId)
                .select("id", "instrument_id", "tuning_id", "bpm", "meter", "duration_ms", "event_count", "created_at"))
                .stream().map(this::toTakeVO).toList();
    }

    public InstrumentPracticeTakeVO detail(Long currentUserId, Long takeId) {
        ensureCurrentUserExists(currentUserId);
        return toTakeVO(requireOwnedTake(currentUserId, takeId));
    }

    /**
     * 保存一段录音；同一乐器满三十段时，先删除最旧记录，再保存最新录制。
     */
    @Transactional
    public SaveInstrumentPracticeTakeResultVO create(Long currentUserId, SaveInstrumentPracticeTakeRequest request) {
        ensureCurrentUserExists(currentUserId);
        validateTakeRequest(request);
        // 同一用户的并发创建先串行化，避免同时读取容量后重复插入。
        if (userMapper.lockUser(currentUserId) == null) {
            throw new BusinessException("INSTRUMENT_OWNER_NOT_FOUND", "当前用户不存在");
        }
        List<InstrumentPracticeTake> existingTakes = instrumentPracticeTakeMapper.selectList(ownedTakes(currentUserId)
                .eq("instrument_id", request.getInstrumentId().trim()).select("id"));
        int overflow = Math.max(0, existingTakes.size() - MAX_TAKES_PER_INSTRUMENT + 1);
        List<Long> overwritten = existingTakes.subList(0, overflow).stream()
                .map(InstrumentPracticeTake::getId).toList();
        if (!overwritten.isEmpty()) {
            instrumentPracticeTakeMapper.deleteByIds(overwritten);
        }

        InstrumentPracticeTake take = new InstrumentPracticeTake();
        take.setOwnerUserId(currentUserId);
        take.setInstrumentId(request.getInstrumentId().trim());
        take.setTuningId(request.getTuningId().trim());
        take.setBpm(request.getBpm());
        take.setMeter(request.getMeter().trim());
        take.setDurationMs(request.getDurationMs());
        take.setEventsJson(writeEvents(request.getEvents()));
        take.setEventCount(request.getEvents().size());
        LocalDateTime now = LocalDateTime.now();
        take.setCreatedAt(now);
        take.setUpdatedAt(now);
        instrumentPracticeTakeMapper.insert(take);

        SaveInstrumentPracticeTakeResultVO result = new SaveInstrumentPracticeTakeResultVO();
        result.setTake(toTakeVO(take));
        result.setOverwrittenTakeIds(overwritten.stream().map(String::valueOf).toList());
        result.setOverwrittenTakeId(overwritten.isEmpty() ? null : String.valueOf(overwritten.getFirst()));
        return result;
    }

    /**
     * 删除当前用户拥有的指定录音。
     */
    @Transactional
    public void delete(Long currentUserId, Long takeId) {
        ensureCurrentUserExists(currentUserId);
        int deleted = instrumentPracticeTakeMapper.delete(new QueryWrapper<InstrumentPracticeTake>()
                .eq("owner_user_id", currentUserId).eq("id", takeId));
        if (deleted == 0) {
            throw new BusinessException("INSTRUMENT_TAKE_NOT_FOUND", "练习片段不存在或无权访问");
        }
    }

    private InstrumentPracticeTake requireOwnedTake(Long currentUserId, Long takeId) {
        InstrumentPracticeTake take = instrumentPracticeTakeMapper.selectOne(new QueryWrapper<InstrumentPracticeTake>()
                .eq("owner_user_id", currentUserId).eq("id", takeId));
        if (take == null) {
            throw new BusinessException("INSTRUMENT_TAKE_NOT_FOUND", "练习片段不存在或无权访问");
        }
        return take;
    }

    private QueryWrapper<InstrumentPracticeTake> ownedTakes(Long currentUserId) {
        return new QueryWrapper<InstrumentPracticeTake>().eq("owner_user_id", currentUserId)
                .orderByAsc("created_at").orderByAsc("id");
    }

    private void ensureCurrentUserExists(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException("INSTRUMENT_OWNER_NOT_FOUND", "当前用户不存在");
        }
    }

    private void validateTakeRequest(SaveInstrumentPracticeTakeRequest request) {
        String instrumentId = normalizeInstrumentId(request.getInstrumentId());
        if (!SUPPORTED_INSTRUMENT_IDS.contains(instrumentId)) {
            throw new BusinessException("INSTRUMENT_ID_INVALID", "乐器类型不支持");
        }
        if (!SUPPORTED_TUNINGS.get(instrumentId).contains(request.getTuningId().trim())) {
            throw new BusinessException("INSTRUMENT_TUNING_INVALID", "调弦设置与乐器不匹配");
        }
        if (!SUPPORTED_METERS.contains(request.getMeter().trim())) {
            throw new BusinessException("INSTRUMENT_METER_INVALID", "拍号不支持");
        }
        if (request.getDurationMs() == null || request.getDurationMs() > MAX_TAKE_DURATION_MS) {
            throw new BusinessException("INSTRUMENT_DURATION_INVALID", "录制时长不能超过 10 分钟");
        }
        if (request.getEvents() == null || request.getEvents().size() > MAX_EVENT_COUNT) {
            throw new BusinessException("INSTRUMENT_EVENT_LIMIT", "演奏事件数量超过上限");
        }
        request.getEvents().forEach(event -> validatePerformanceEvent(event, instrumentId, request.getDurationMs()));
    }

    private void validatePerformanceEvent(PerformanceEventRequest event, String instrumentId, Long durationMs) {
        if (event == null || !SUPPORTED_EVENT_TYPES.contains(event.getType())) {
            throw new BusinessException("INSTRUMENT_EVENT_TYPE_INVALID", "演奏事件类型不支持");
        }
        if (!instrumentId.equals(event.getInstrumentId())) {
            throw new BusinessException("INSTRUMENT_EVENT_MISMATCH", "演奏事件的乐器与录音不一致");
        }
        if (event.getAt() == null || event.getAt() > durationMs) {
            throw new BusinessException("INSTRUMENT_EVENT_TIME_INVALID", "演奏事件时间超出录制范围");
        }
        if (("note".equals(event.getType()) || "bend".equals(event.getType()))
                && !StringUtils.hasText(event.getStringId())) {
            throw new BusinessException("INSTRUMENT_EVENT_STRING_REQUIRED", "当前演奏事件缺少琴弦标识");
        }
        if ("note".equals(event.getType()) && event.getMidi() == null) {
            throw new BusinessException("INSTRUMENT_EVENT_MIDI_REQUIRED", "音符事件缺少 MIDI 音高");
        }
    }

    private String normalizeInstrumentId(String instrumentId) {
        return StringUtils.hasText(instrumentId) ? instrumentId.trim() : "";
    }

    private String writeEvents(List<PerformanceEventRequest> events) {
        try {
            List<PerformanceEventRequest> sortedEvents = events.stream()
                    .sorted(Comparator.comparingLong(PerformanceEventRequest::getAt))
                    .toList();
            return objectMapper.writeValueAsString(sortedEvents);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("INSTRUMENT_EVENT_SERIALIZE_FAILED", "演奏事件保存失败");
        }
    }

    private InstrumentPracticeTakeVO toTakeVO(InstrumentPracticeTake take) {
        InstrumentPracticeTakeVO view = new InstrumentPracticeTakeVO();
        view.setId(take.getId() == null ? null : String.valueOf(take.getId()));
        view.setInstrumentId(take.getInstrumentId());
        view.setTuningId(take.getTuningId());
        view.setBpm(take.getBpm());
        view.setMeter(take.getMeter());
        view.setDurationMs(take.getDurationMs());
        view.setEventCount(take.getEventCount());
        if (take.getEventsJson() != null) {
            view.setEvents(readEvents(take.getEventsJson()));
        }
        view.setCreatedAt(take.getCreatedAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli());
        return view;
    }

    private List<PerformanceEventRequest> readEvents(String eventsJson) {
        try {
            return objectMapper.readValue(eventsJson, PERFORMANCE_EVENT_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("INSTRUMENT_EVENT_DATA_INVALID", "已保存的演奏事件无法读取");
        }
    }
}
