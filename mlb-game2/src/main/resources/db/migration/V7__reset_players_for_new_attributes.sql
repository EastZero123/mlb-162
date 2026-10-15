-- 능력치 체계 변경(타자·투수 각 11개, 구종 능력치 제거)에 따라 개발 중 생성한 선수 데이터를 모두 삭제
-- 회원(users)과 구단(teams)은 유지
DELETE FROM draft_results;
DELETE FROM combine_summaries;
DELETE FROM combine_results;
DELETE FROM player_pitches;
DELETE FROM player_attributes;
DELETE FROM players;
