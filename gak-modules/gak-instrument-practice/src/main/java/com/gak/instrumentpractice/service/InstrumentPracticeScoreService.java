package com.gak.instrumentpractice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gak.framework.exception.BusinessException;
import com.gak.instrumentpractice.domain.InstrumentPracticeScore;
import com.gak.instrumentpractice.dto.SaveInstrumentPracticeScoreRequest;
import com.gak.instrumentpractice.mapper.InstrumentPracticeScoreMapper;
import com.gak.instrumentpractice.vo.InstrumentPracticeScoreVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 按账号保存与管理曲谱。
 */
@Service
public class InstrumentPracticeScoreService {

    private static final int MAX_SCORES = 20;
    private final InstrumentPracticeScoreMapper scoreMapper;

    public InstrumentPracticeScoreService(InstrumentPracticeScoreMapper scoreMapper) {
        this.scoreMapper = scoreMapper;
    }

    public List<InstrumentPracticeScoreVO> list(Long ownerUserId) {
        return scoreMapper.selectList(ownedScores(ownerUserId)
                        .orderByDesc(InstrumentPracticeScore::getUpdatedAt, InstrumentPracticeScore::getId))
                .stream().map(this::toView).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InstrumentPracticeScoreVO create(Long ownerUserId, SaveInstrumentPracticeScoreRequest request) {
        // 串行化同一账号的新增请求，后续请求在拿到锁后读取最新条数。
        if (scoreMapper.lockOwner(ownerUserId) == null) {
            throw new BusinessException("INSTRUMENT_OWNER_NOT_FOUND", "当前用户不存在");
        }
        if (scoreMapper.selectCount(ownedScores(ownerUserId)) >= MAX_SCORES) {
            throw new BusinessException("INSTRUMENT_SCORE_LIMIT", "最多保存 20 首曲谱，请先删除一首");
        }

        InstrumentPracticeScore score = toScore(request);
        score.setOwnerUserId(ownerUserId);
        scoreMapper.insert(score);
        return toView(score);
    }

    @Transactional
    public InstrumentPracticeScoreVO update(Long ownerUserId, Long scoreId, SaveInstrumentPracticeScoreRequest request) {
        InstrumentPracticeScore score = toScore(request);
        requireOwnedScore(scoreMapper.update(score, ownedScore(ownerUserId, scoreId)));
        score.setId(scoreId);
        return toView(score);
    }

    @Transactional
    public void delete(Long ownerUserId, Long scoreId) {
        requireOwnedScore(scoreMapper.delete(ownedScore(ownerUserId, scoreId)));
    }

    private LambdaQueryWrapper<InstrumentPracticeScore> ownedScores(Long ownerUserId) {
        return new LambdaQueryWrapper<InstrumentPracticeScore>()
                .eq(InstrumentPracticeScore::getOwnerUserId, ownerUserId);
    }

    private LambdaQueryWrapper<InstrumentPracticeScore> ownedScore(Long ownerUserId, Long scoreId) {
        return ownedScores(ownerUserId).eq(InstrumentPracticeScore::getId, scoreId);
    }

    private void requireOwnedScore(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException("INSTRUMENT_SCORE_NOT_FOUND", "曲谱不存在或无权访问");
        }
    }

    private InstrumentPracticeScore toScore(SaveInstrumentPracticeScoreRequest request) {
        String instrumentId = request.instrumentId() == null ? "" : request.instrumentId().strip();
        String tuningId = request.tuningId() == null ? "" : request.tuningId().strip();
        if ((!instrumentId.isEmpty() || !tuningId.isEmpty())
                && !InstrumentPracticeTakeService.supportsTuning(instrumentId, tuningId)) {
            throw new BusinessException("INSTRUMENT_TUNING_INVALID", "调弦设置与乐器不匹配");
        }

        InstrumentPracticeScore score = new InstrumentPracticeScore();
        score.setTitle(request.title().strip());
        score.setNotation(request.notation());
        score.setTonic(request.tonic());
        score.setMode(request.mode());
        score.setMeter(request.meter());
        score.setBpm(request.bpm());
        score.setInstrumentId(instrumentId);
        score.setTuningId(tuningId);
        score.setUpdatedAt(LocalDateTime.now());
        return score;
    }

    private InstrumentPracticeScoreVO toView(InstrumentPracticeScore score) {
        return new InstrumentPracticeScoreVO(String.valueOf(score.getId()), score.getTitle(), score.getNotation(),
                score.getTonic(), score.getMode(), score.getMeter(), score.getBpm(),
                score.getInstrumentId(), score.getTuningId());
    }
}
