-- 불펜투수(RP) 포지션을 마무리투수(CP)로 변경
UPDATE players SET position = 'CP' WHERE position = 'RP';
