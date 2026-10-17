-- 시리즈 기록에 그 시리즈를 뛴 소속 구단을 남긴다 (시즌 기록을 연도·레벨·구단별로 나눠 보여주기 위해)
ALTER TABLE series_records ADD COLUMN team_id BIGINT;

-- 기존 기록 복원. 트레이드는 시즌당 최대 1번, FA 이적은 시즌 시작 전에만 일어나므로
--   트레이드가 실제로 반영된 시즌(시즌 소속 = 트레이드 행선지)의 트레이드 시점까지 → 이전 구단
--   그 밖 → 시즌 소속 구단 (트레이드가 기록만 되고 이동이 저장되지 않았던 예전 버그 시즌도 여기에 해당)
UPDATE series_records sr
SET team_id = COALESCE(
        (SELECT te.from_team_id
         FROM trade_events te
                  JOIN player_seasons ps ON ps.player_id = te.player_id AND ps.season_year = te.season_year
         WHERE te.player_id = sr.player_id
           AND te.season_year = sr.season_year
           AND te.kind = 'TRADE'
           AND ps.team_id = te.to_team_id
           AND sr.series_no <= te.after_series
         ORDER BY te.id
         LIMIT 1),
        (SELECT ps.team_id
         FROM player_seasons ps
         WHERE ps.player_id = sr.player_id
           AND ps.season_year = sr.season_year),
        (SELECT p.team_id
         FROM players p
         WHERE p.id = sr.player_id));

ALTER TABLE series_records MODIFY team_id BIGINT NOT NULL;
ALTER TABLE series_records ADD CONSTRAINT fk_series_records_team FOREIGN KEY (team_id) REFERENCES teams (id);
