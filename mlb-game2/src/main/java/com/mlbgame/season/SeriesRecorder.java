package com.mlbgame.season;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlbgame.simulation.series.SeriesInput;
import com.mlbgame.simulation.series.SeriesResult;
import com.mlbgame.simulation.series.SeriesSimulator;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시리즈 실행 + 저장 + 재현. "DB + 시드 재현" 원칙의 진입점 (요구사항 4장).
 *
 * <ul>
 *   <li>{@link #play}: 새 시드로 시리즈를 계산하고 입력(JSON)·요약(JSON)만 저장</li>
 *   <li>{@link #replay}: 저장된 입력으로 다시 계산 → 경기별 상세·하이라이트를 볼 때 사용</li>
 * </ul>
 * 시즌 진행 흐름(어느 시리즈를 언제 진행할지, 능력치 반영 등)은 M3 에서 이 서비스를 호출한다.
 */
@Service
public class SeriesRecorder {

    private final SeriesSimulator simulator;
    private final SeriesRecordRepository repository;
    private final ObjectMapper objectMapper;

    public SeriesRecorder(SeriesSimulator simulator, SeriesRecordRepository repository, ObjectMapper objectMapper) {
        this.simulator = simulator;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public static long newSeed() {
        return ThreadLocalRandom.current().nextLong();
    }

    @Transactional
    public PlayedSeries play(Long playerId, int seasonYear, int seriesNo, Long teamId, SeriesInput input) {
        SeriesResult result = simulator.simulate(input);
        SeriesRecord record = repository.save(new SeriesRecord(playerId, seasonYear, seriesNo, input.seed(),
                input.level(), teamId, toJson(input), result.wins(), result.losses(), toJson(SeriesSummary.of(result))));
        return new PlayedSeries(record, result);
    }

    @Transactional(readOnly = true)
    public SeriesResult replay(Long recordId) {
        SeriesRecord record = repository.findById(recordId).orElseThrow();
        return simulator.simulate(readInput(record));
    }

    public SeriesInput readInput(SeriesRecord record) {
        try {
            return objectMapper.readValue(record.getInputJson(), SeriesInput.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("시리즈 입력을 읽을 수 없습니다: " + record.getId(), e);
        }
    }

    public SeriesSummary readSummary(SeriesRecord record) {
        try {
            return objectMapper.readValue(record.getSummaryJson(), SeriesSummary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("시리즈 요약을 읽을 수 없습니다: " + record.getId(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 변환 실패", e);
        }
    }

    public record PlayedSeries(SeriesRecord record, SeriesResult result) {
    }
}
