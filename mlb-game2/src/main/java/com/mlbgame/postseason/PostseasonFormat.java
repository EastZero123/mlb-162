package com.mlbgame.postseason;

import com.mlbgame.award.AwardType;
import com.mlbgame.league.standings.StandingsTable;
import java.util.List;

/**
 * 모드별 포스트시즌 방식: 라운드 목록, 첫 대진, 다음 라운드 대진, 시리즈 MVP.
 * 대진표는 모드와 상관없이 {@link Bracket} 으로 저장한다.
 */
public interface PostseasonFormat {

    /** 1번 라운드부터 순서대로 */
    List<? extends RoundSpec> rounds();

    default RoundSpec round(int number) {
        return rounds().get(number - 1);
    }

    /** 마지막 라운드(우승 결정전) 번호 */
    default int finalRound() {
        return rounds().size();
    }

    /** 정규시즌 순위표로 첫 라운드 대진 */
    Bracket initial(StandingsTable table);

    /** completedRound 가 모두 끝난 뒤 다음 라운드 대진 (마지막 라운드 뒤에는 호출하지 않는다) */
    void createNextRound(Bracket bracket, int completedRound);

    /** 내 팀이 이 라운드를 이겼을 때 받을 수 있는 시리즈 MVP (없으면 null) */
    SeriesMvp seriesMvp(int round);

    /**
     * @param marker 수상 기록의 시리즈 번호 자리 (정규시즌 시리즈 수보다 큰 값으로 구분)
     */
    record SeriesMvp(AwardType type, int marker) {
    }
}
