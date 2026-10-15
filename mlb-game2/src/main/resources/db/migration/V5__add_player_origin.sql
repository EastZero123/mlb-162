-- 선수 출신: 국적, 출신 학교 지역 (기존 행은 기본값으로 채움)
ALTER TABLE players ADD COLUMN nationality VARCHAR(10) NOT NULL DEFAULT 'USA';
ALTER TABLE players ADD COLUMN school_region VARCHAR(20) NOT NULL DEFAULT 'US_EAST';
