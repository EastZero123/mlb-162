-- KBO 10개 구단 (로고 미사용, 팀명 텍스트 + 팀 컬러만). 단일 리그·지구 없음. 설계: docs/kbo-mode.md 3.1
-- 코드는 MLB 구단 코드와 겹치지 않게 정한다 (teams.code UNIQUE)
INSERT INTO teams (code, name, name_ko, league, division, primary_color, league_mode) VALUES
('LG',  'LG Twins',       'LG 트윈스',    'KBO', '-', '#C30452', 'KBO'),
('HH',  'Hanwha Eagles',  '한화 이글스',  'KBO', '-', '#FF6600', 'KBO'),
('SSG', 'SSG Landers',    'SSG 랜더스',   'KBO', '-', '#CE0E2D', 'KBO'),
('SS',  'Samsung Lions',  '삼성 라이온즈', 'KBO', '-', '#074CA1', 'KBO'),
('NC',  'NC Dinos',       'NC 다이노스',  'KBO', '-', '#315288', 'KBO'),
('KT',  'KT Wiz',         'KT 위즈',      'KBO', '-', '#000000', 'KBO'),
('LT',  'Lotte Giants',   '롯데 자이언츠', 'KBO', '-', '#041E42', 'KBO'),
('KIA', 'KIA Tigers',     'KIA 타이거즈', 'KBO', '-', '#EA0029', 'KBO'),
('DS',  'Doosan Bears',   '두산 베어스',  'KBO', '-', '#131230', 'KBO'),
('KW',  'Kiwoom Heroes',  '키움 히어로즈', 'KBO', '-', '#570514', 'KBO');
