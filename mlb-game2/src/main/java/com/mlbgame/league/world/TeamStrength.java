package com.mlbgame.league.world;

import com.mlbgame.league.LeagueLevel;
import com.mlbgame.simulation.game.TeamRatings;
import java.util.List;

/**
 * 팀 전력 계산. 메이저리그는 1군 로스터에서, 마이너리그는 레벨 평균 + 팜 점수로 구한다.
 *
 * <pre>
 * 타선   = 주전(LINEUP) 9명 평균 OVR
 * 선발진 = 선발(ROTATION) 5명 평균
 * 불펜   = 불펜(BULLPEN) 7명 + 마무리(CLOSER) 1명 평균
 * 마이너 = 레벨 평균 + (팜 점수 − 60) / 10   (팜 90 → +3, 30 → −3, 세 부문 동일)
 * </pre>
 */
public final class TeamStrength {

    static final int FARM_BASELINE = 60;

    private TeamStrength() {
    }

    public record RosterSlot(RosterRole role, double overall) {
    }

    public static TeamRatings fromRoster(List<RosterSlot> roster) {
        return new TeamRatings(
                average(roster, RosterRole.LINEUP),
                average(roster, RosterRole.ROTATION),
                average(roster, RosterRole.BULLPEN, RosterRole.CLOSER));
    }

    public static TeamRatings fromNpcs(List<NpcPlayer> roster) {
        return fromRoster(roster.stream().map(n -> new RosterSlot(n.getRosterRole(), n.getOverall())).toList());
    }

    public static TeamRatings forMinors(LeagueLevel level, int farmScore) {
        int rating = level.getAverageRating() + (int) Math.round((farmScore - FARM_BASELINE) / 10.0);
        return TeamRatings.uniform(rating);
    }

    private static int average(List<RosterSlot> roster, RosterRole... roles) {
        List<RosterRole> wanted = List.of(roles);
        return (int) Math.round(roster.stream()
                .filter(s -> wanted.contains(s.role()))
                .mapToDouble(RosterSlot::overall)
                .average()
                .orElse(LeagueLevel.MLB.getAverageRating()));
    }
}
