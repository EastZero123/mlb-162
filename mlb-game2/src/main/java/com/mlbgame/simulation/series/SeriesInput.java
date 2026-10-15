package com.mlbgame.simulation.series;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.simulation.PlayerSnapshot;
import com.mlbgame.simulation.game.TeamRatings;
import com.mlbgame.simulation.rules.PitcherUsage;

/**
 * 시리즈 한 번의 입력 전체. 이 값과 시드만 있으면 같은 결과를 다시 만들 수 있으므로 JSON 으로 통째로 저장한다.
 *
 * @param seed           시드 (엔진 내부 랜덤은 이 시드의 난수열 하나만 사용)
 * @param level          소속 리그 레벨
 * @param home           홈 시리즈 여부
 * @param team           내 팀 전력 (타선·선발·불펜)
 * @param opponent       상대 팀 전력
 * @param games          경기 수 (기본 3)
 * @param player         시리즈 시작 시점 내 선수
 * @param fatigue        시작 피로도
 * @param usage          시작 투수 등판 상태 (타자면 무시)
 * @param sidelined      부상 결장 중 — 내 선수는 출전하지 않고 팀 경기만 진행
 */
public record SeriesInput(
        long seed,
        LeagueLevel level,
        boolean home,
        TeamRatings team,
        TeamRatings opponent,
        int games,
        PlayerSnapshot player,
        int fatigue,
        PitcherUsage usage,
        boolean sidelined
) {

    public static final int DEFAULT_GAMES = 3;
}
