package com.mlbgame.combine;

import com.mlbgame.common.Dice;
import com.mlbgame.common.Messages;
import com.mlbgame.config.GameBalance;
import com.mlbgame.player.Education;
import com.mlbgame.player.Player;
import com.mlbgame.player.PlayerService;
import com.mlbgame.player.PlayerStage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 드래프트 컴바인. 능력치는 바꾸지 않고, 당일 컨디션 랜덤이 섞인 기록으로 드래프트 평가 점수만 만든다.
 */
@Service
public class CombineService {

    /** 종목마다 따로 붙는 작은 편차 (표준편차, 최대 폭) */
    static final double TEST_NOISE_SD = 4;
    static final double TEST_NOISE_MAX = 10;

    private final PlayerService playerService;
    private final CombineResultRepository resultRepository;
    private final CombineSummaryRepository summaryRepository;
    private final GameBalance balance;
    private final Dice dice;

    public CombineService(PlayerService playerService, CombineResultRepository resultRepository,
                          CombineSummaryRepository summaryRepository, GameBalance balance, Dice dice) {
        this.playerService = playerService;
        this.resultRepository = resultRepository;
        this.summaryRepository = summaryRepository;
        this.balance = balance;
        this.dice = dice;
    }

    @Transactional
    public CombineSummary run(Long userId, Long playerId, InterviewAnswer answer) {
        Player player = playerService.getOwned(userId, playerId);
        player.completeCombine(); // CREATED 단계가 아니면 예외 — 컴바인은 1회만

        DayCondition condition = DayCondition.roll(dice);
        List<CombineResult> results = new ArrayList<>();
        for (CombineTest test : CombineTest.forPosition(player.getPosition())) {
            double noise = clamp(dice.nextGaussian() * TEST_NOISE_SD, -TEST_NOISE_MAX, TEST_NOISE_MAX);
            int score = (int) Math.round(clamp(test.baseScore(player) + condition.getOffset() + noise, 1, 100));
            results.add(new CombineResult(player.getId(), test, score));
        }
        resultRepository.saveAll(results);

        InterviewAnswer preference = InterviewAnswer.values()[dice.nextInt(InterviewAnswer.values().length)];
        int interviewBonus = answer == preference ? InterviewAnswer.MATCH_BONUS : InterviewAnswer.DEFAULT_BONUS;
        int ageBonus = player.getEducation() == Education.HIGH_SCHOOL ? balance.highSchoolScoutBonus() : 0;

        double testAverage = results.stream().mapToInt(CombineResult::getScore).average().orElse(0);
        double total = Math.round((testAverage + interviewBonus + ageBonus) * 10) / 10.0;
        CombineGrade grade = CombineGrade.of(total);

        CombineSummary summary = new CombineSummary(player.getId(), condition, answer, preference,
                interviewBonus, ageBonus, total, grade, scoutComment(grade, results));
        return summaryRepository.save(summary);
    }

    @Transactional(readOnly = true)
    public CombineReport report(Long userId, Long playerId) {
        Player player = playerService.getOwned(userId, playerId);
        if (player.getStage() == PlayerStage.CREATED) {
            return null;
        }
        CombineSummary summary = summaryRepository.findById(playerId).orElseThrow();
        return new CombineReport(summary, resultRepository.findByPlayerIdOrderByIdAsc(playerId));
    }

    private String scoutComment(CombineGrade grade, List<CombineResult> results) {
        CombineResult best = results.stream().max(Comparator.comparingInt(CombineResult::getScore)).orElseThrow();
        CombineTest test = best.getTestType();
        String record = String.format("%." + test.getDecimals() + "f", best.getRecordValue());
        return grade.getComment() + Messages.get("combine.comment.best", test.getLabel(), record, test.getUnit());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record CombineReport(CombineSummary summary, List<CombineResult> results) {
    }
}
