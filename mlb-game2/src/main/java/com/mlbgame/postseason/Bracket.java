package com.mlbgame.postseason;

import java.util.ArrayList;
import java.util.List;

/**
 * 포스트시즌 대진표 (postseasons.bracket_json 에 저장). Jackson 직렬화를 위해 public 필드 POJO.
 */
public class Bracket {

    /** 리그별 시드 */
    public List<Seed> seeds = new ArrayList<>();
    /** 진행된(또는 진행할) 모든 시리즈 */
    public List<Series> series = new ArrayList<>();

    public static class Seed {
        public String league;
        public int seed;
        public Long teamId;
        public boolean divisionWinner;

        public Seed() {
        }

        public Seed(String league, int seed, Long teamId, boolean divisionWinner) {
            this.league = league;
            this.seed = seed;
            this.teamId = teamId;
            this.divisionWinner = divisionWinner;
        }
    }

    public static class Series {
        public int round;
        /** "AL" / "NL" / "WS" */
        public String league;
        public Long highTeamId;
        public Long lowTeamId;
        public int highSeed;
        public int lowSeed;
        public int highWins;
        public int lowWins;
        public Long winnerId;
        /** 내 소속 구단이 뛴 시리즈 */
        public boolean userTeam;
        /** 경기별 점수 "5-3" (상위 시드 기준) */
        public List<String> scores = new ArrayList<>();
        /** 내 선수 시리즈 기록 요약 (직접 뛴 경우) */
        public String userSummary;

        public Series() {
        }

        public Series(int round, String league, Seed high, Seed low) {
            this.round = round;
            this.league = league;
            this.highTeamId = high.teamId;
            this.lowTeamId = low.teamId;
            this.highSeed = high.seed;
            this.lowSeed = low.seed;
        }

        public boolean finished() {
            return winnerId != null;
        }

        public boolean involves(Long teamId) {
            return teamId.equals(highTeamId) || teamId.equals(lowTeamId);
        }
    }

    public List<Series> round(int round) {
        return series.stream().filter(s -> s.round == round).toList();
    }

    public Seed seed(String league, int seed) {
        return seeds.stream().filter(s -> s.league.equals(league) && s.seed == seed).findFirst().orElse(null);
    }

    /** 아직 탈락하지 않았는가 */
    public boolean alive(Long teamId) {
        boolean seeded = seeds.stream().anyMatch(s -> s.teamId.equals(teamId));
        boolean lost = series.stream().anyMatch(s -> s.finished() && s.involves(teamId) && !s.winnerId.equals(teamId));
        return seeded && !lost;
    }

    public Long champion() {
        return champion(PostseasonRound.WORLD_SERIES.getNumber());
    }

    /** 마지막 라운드 승자 */
    public Long champion(int finalRound) {
        return series.stream().filter(s -> s.round == finalRound && s.finished())
                .map(s -> s.winnerId).findFirst().orElse(null);
    }
}
