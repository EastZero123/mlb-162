package com.mlbgame.simulation.game;

/**
 * 경기 한 판의 입력. 시드 재현을 위해 경기 시작 시점 값으로 고정한다.
 *
 * @param home     내 팀이 홈 팀인지
 * @param team     내 팀 다른 선수들의 전력 (타선·선발·불펜)
 * @param opponent 상대 팀 전력
 * @param hitter   내 선수가 타자일 때 (투수면 null)
 * @param pitcher  내 선수가 투수일 때 (타자면 null)
 */
public record GameSetup(
        boolean home,
        TeamRatings team,
        TeamRatings opponent,
        UserHitter hitter,
        UserPitcher pitcher
) {

    public static GameSetup forHitter(boolean home, TeamRatings team, TeamRatings opponent, UserHitter hitter) {
        return new GameSetup(home, team, opponent, hitter, null);
    }

    public static GameSetup forPitcher(boolean home, TeamRatings team, TeamRatings opponent, UserPitcher pitcher) {
        return new GameSetup(home, team, opponent, null, pitcher);
    }

    /** 세 부문 전력이 같은 팀끼리 (테스트·검증용) */
    public static GameSetup forHitter(boolean home, int teamRating, int opponentRating, UserHitter hitter) {
        return forHitter(home, TeamRatings.uniform(teamRating), TeamRatings.uniform(opponentRating), hitter);
    }

    public static GameSetup forPitcher(boolean home, int teamRating, int opponentRating, UserPitcher pitcher) {
        return forPitcher(home, TeamRatings.uniform(teamRating), TeamRatings.uniform(opponentRating), pitcher);
    }
}
