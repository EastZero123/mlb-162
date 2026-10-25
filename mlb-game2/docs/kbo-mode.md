# KBO 모드 설계 (v0.1 초안)

현재 게임 전체를 **MLB 모드**로 두고, 같은 서비스 구조 위에 **KBO 모드**를 추가한다.
상단 탭으로 모드를 전환하고(언어 전환처럼), 내부 규칙은 모드별 규칙셋으로 갈아 끼운다.

## 0. 결정 사항 (2026-10-24)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 능력치 척도 | **모드별 독립.** KBO 1군 평균 = OVR 65 = KBO 리그 평균 기록. 포스팅(모드 간 이적)은 없음 |
| 2 | 선수 슬롯 | **모드별 3개씩** (계정당 MLB 3 + KBO 3) |
| 3 | 구단 명칭 | **실제 KBO 10개 구단명** 사용 (MLB와 같은 원칙: 로고 미사용, 텍스트 + 팀 컬러, 시드 데이터로 교체 가능) |
| 4 | 1차 범위 | **기본 규칙 바꾸기까지.** 군 복무·국가대표는 2차 — 단, 1차 구조에서 끼워 넣을 자리를 만들어 둔다 (7장) |

### 0.1 OVR 65 의 "무게감" 차이
척도는 모드별로 따로 두지만, 같은 OVR 이라도 체감은 아래 세 가지로 달라진다.
- **기록 환경**: KBO 65 는 KBO 리그 평균 기록(타율 .270대, 낮은 삼진율)을 낸다. 같은 65라도 숫자가 다르게 보인다.
- **경제 규모**: 연봉·계약금이 원화 스케일 (MLB 65 ≈ $8M, KBO 65 ≈ 1.5억)
- **화면 표기**: OVR 옆에 리그 기준을 표시한다 (예: `OVR 72.4 · KBO 기준`). 다른 모드의 수치와 직접 비교하지 않는다는 신호

나중에 포스팅을 넣게 되면 척도를 통일할 필요는 없다. 이적하는 순간에만 변환식(예: KBO OVR → MLB OVR)을 한 번 적용하면 된다.

## 1. 모드 전환 UX

- 상단 헤더에 `MLB | KBO` 탭. 선택한 모드는 쿠키(`league_mode`, 기본 MLB)에 저장 → 로그인 전 공개 페이지(랭킹)에서도 동작
  - `GET /mode/{mlb|kbo}` → 쿠키 설정 → 대시보드(비로그인은 랭킹)로 리다이렉트. 외부 주소로 보내는 redirect 파라미터는 받지 않음
- 탭이 바꾸는 것: 대시보드 선수 목록·빈 슬롯, 선수 생성 대상 모드, 랭킹, 브랜드 문구(`⚾ KBO 선수 키우기`), 테마 강조색
- 선수 화면(`/players/{id}/...`)은 URL 그대로 — **모드는 선수에 저장**되어 있어서 그 선수의 규칙셋으로 그린다
  - 탭과 다른 모드의 선수 화면에 들어오면 쿠키를 그 선수의 모드로 맞춘다 (헤더와 화면이 어긋나지 않게)
- 컨트롤러·템플릿은 복제하지 않는다. 문구·금액·팀명 표시는 규칙셋에서 받는다

## 2. 핵심 구조: `LeagueMode` + `LeagueRuleset`

```
com.mlbgame.league
 ├─ LeagueMode            enum { MLB, KBO }                       (K1)
 └─ mode
     ├─ LeagueRuleset     모드별 규칙 묶음 (인터페이스)              (K1)
     ├─ MlbRuleset        현재 동작을 그대로 옮긴 구현               (K1)
     ├─ KboRuleset        KBO 구현                                  (K3)
     ├─ LeagueRulesets    LeagueMode → LeagueRuleset 정적 조회       (K1)
     └─ ModeCookie / ModeAdvice  쿠키 읽기·쓰기, 모든 화면 모델에 현재 모드 추가 (K2)
```
- 규칙셋은 상태 없는 순수 규칙이라 `LeagueRulesets.of(player | world | level | mode)` 정적 조회로 찾는다 (서비스 생성자 변경 없음)
- 모드별 수치 묶음은 규칙이 있는 패키지에 record 로 둔다: `PayrollRules`(연봉·예산·통화), `DraftRules`, `PromotionRules.Params`, `PostseasonFormat`·`RoundSpec`

`LeagueRuleset` 이 제공하는 것 (기존 static 규칙 클래스는 순수 함수로 남기고, 상수 대신 규칙셋의 파라미터 record 를 인자로 받게 바꾼다 → 기존 단위 테스트 구조 유지)

| 묶음 | 내용 | 현재 위치 |
|---|---|---|
| 레벨 | 레벨 순서(하위→최상위), 최상위 레벨, 레벨 평균 OVR, 팀명 접미사 | `LeagueLevel` |
| 일정 | 시즌 시리즈 수, 시리즈별 경기 수, 대진 생성 | `ScheduleGenerator`, `PlayerSeason.SERIES_PER_SEASON` |
| 순위·포스트시즌 | 리그/지구 구성, 진출 팀 수, 라운드 정의(경기 수·홈 패턴·어드밴티지), 대진 진행 | `StandingsTable`, `PostseasonEngine`, `PostseasonRound` |
| 승강 | 판정 주기, 성적 기준값(OPS·ERA), 승격·강등 기준 | `PromotionRules` |
| 시상 | 상 목록·포인트·보너스, 판정 시점(월간·올스타·시즌), 기준값 | `AwardType`, `AwardService`, `AwardRules` |
| 경제 | 통화 포맷, 최저 연봉, 레벨별 연봉, 시장가 공식, 예산·사치세(경쟁균형세) | `Payroll`, `Market`, `StatFormat.money` |
| 계약 | 서비스 연수 계산, 연봉 단계(조정/협상), FA 자격, 연장 계약 | `ContractRules`, `ContractService` |
| 드래프트 | 구단 수 × 라운드 수, 계약금 곡선, 시작 레벨 | `DraftService` |
| 트레이드 | 가능 레벨, 기간 | `TradeRules` |
| 시뮬레이션 | 리그 평균 기록(baseline). 능력치 계수는 공통 | `SimulationBalance` |
| 이름·국적 | NPC 이름 생성 분포, 외국인 선수 슬롯 | `NameGenerator`, `WorldGenerator` |

### 2.1 레벨은 하나의 enum 에 모드를 붙여 확장
`LeagueLevel` 에 `FUTURES`, `KBO` 를 추가하고 각 값에 `mode` 를 둔다.
- DB 의 레벨 컬럼은 전부 `VARCHAR(10)` + enum 이름 저장이라 **마이그레이션 없이 확장 가능** (`FUTURES` 7자)
- `level == LeagueLevel.MLB` (약 30곳) → `level.isTop()` 으로 교체
- `values()[ordinal ± 1]` (승강) → `ruleset.levels()` 안에서 이전/다음

```
ROOKIE(MLB, "루키리그", 35)   A(MLB, 45)   AA(MLB, 52)   AAA(MLB, 58)   MLB(MLB, 65, top)
FUTURES(KBO, "퓨처스리그", "퓨처스", 52)   KBO(KBO, "KBO 리그", "", 65, top)
```

### 2.2 DB 변경
| 테이블 | 변경 |
|---|---|
| `teams` | (K1, V22) `league_mode VARCHAR(5) NOT NULL DEFAULT 'MLB'` / (K3) KBO 10개 구단 INSERT (`league='KBO'`, `division='-'`) |
| `players` | (K1, V22) `league_mode ... DEFAULT 'MLB'` — 규칙셋 선택, 슬롯 계산·대시보드 필터(K2) |
| `league_worlds` | (K1, V22) `league_mode ... DEFAULT 'MLB'` — 월드 생성 시 선수 모드를 복사, 월드만 가진 서비스(순위·NPC 기록·시즌 전환)가 규칙셋을 찾는 데 쓴다 |
| `ranking_entries` | (K1, V22) `league_mode ... DEFAULT 'MLB'` — 은퇴 스냅샷에 저장, 모드별 랭킹 필터(K2) |
| 그 외 | 시즌·시리즈 기록은 레벨 값으로 모드를 알 수 있어 변경 없음 |

구단 조회는 용도로 나눴다 (K1 완료)
- **리그 구성원**(대진·지명·월드 생성·순위): `findByLeagueModeOrderByIdAsc(mode)` — 드래프트, 리그 월드 생성, 일정, 순위표
- **id → 구단 표시용 맵**: `findAllByOrderByIdAsc()` 유지 — 모드와 상관없이 id 로 찾으면 맞다 (커리어·랭킹·포스트시즌·기록 표·NPC 기록·시즌 전환 헤드라인)

## 3. KBO 기본 규칙 (1차 범위)

### 3.1 리그·구단
- 10개 구단, 단일 리그, 지구 없음 (KBO 2025 기준 구단명·대표색, 코드는 MLB 코드와 겹치지 않게)
  - LG 트윈스, 한화 이글스, SSG 랜더스, 삼성 라이온즈, NC 다이노스, KT 위즈, 롯데 자이언츠, KIA 타이거즈, 두산 베어스, 키움 히어로즈
- 레벨 2단계: **퓨처스리그 → KBO 리그(1군)**. 퓨처스 팀명은 `"LG 트윈스 퓨처스"`
- 구단 재정 성향(`Market`): 모기업 규모를 흉내 낸 기본 배정 (설정값, 밸런스 때 조정)
- NPC 로스터: 1군 28명 기준(출전 26) + **외국인 선수 3명** 슬롯 (외국인은 능력치 상위 분포, 1년 계약, 국적 미국/중남미 등)

### 3.2 일정 — 54시리즈 유지, 144경기
- **3연전 36 + 2연전 18 = 54시리즈, 144경기.** 상대 9팀 × (3연전 4 + 2연전 2) = 팀당 16경기, 홈·원정 8:8
- 시리즈 수를 MLB 와 똑같이 54로 두는 이유: 훈련 횟수(시즌 54회)·성장 곡선·월간 시상 주기(9시리즈)·연봉 적립(1/54)이 그대로 유지돼 밸런스 재작업이 크게 줄어든다
- 엔진은 이미 시리즈별 경기 수를 받는다(`SeriesInput.games`). 순위 생성(`StandingsGenerator`)과 시상 판정의 "시리즈당 3경기" 가정만 일정의 경기 수를 따르게 바꾼다
- 세이버 지표의 시즌 경기 수(`AdvancedStats.SEASON_GAMES = 162`) → 규칙셋에서 144

### 3.3 승격·강등 (1군 등록·말소)
- KBO 는 1군 등록·말소가 잦다 → **판정 주기 3시리즈**(약 7~9경기), 말소되면 다음 판정까지 재등록 불가 (실제 "말소 후 10일" 규칙과 비슷한 효과)
- 성적 기준값을 KBO 평균에 맞춤 (타자 OPS 약 .760, 선발 ERA 약 4.70 — 리그 평균 기록 보정 후 확정)
- 레전드 강등 면제(명예의 전당 150점)는 최상위 레벨 기준으로 그대로 적용

### 3.4 포스트시즌 — 5팀 계단식
| 라운드 | 대진 | 방식 |
|---|---|---|
| 와일드카드 결정전 | 4위 vs 5위 | 최대 2경기, 모두 4위 홈, **4위 1승 안고 시작** (1승 또는 무승부면 통과) |
| 준플레이오프 | 3위 vs WC 승자 | 5전 3선승 (2-2-1) |
| 플레이오프 | 2위 vs 준PO 승자 | 5전 3선승 (2-2-1) |
| 한국시리즈 | 1위 vs PO 승자 | 7전 4선승 (2-3-2) |

- 라운드 수가 4로 MLB 와 같아서 `postseasons` 테이블·진행 흐름은 그대로. 바뀌는 것은 라운드 정의(이름·경기 수·홈 패턴·**초기 승수**)와 다음 라운드 대진 규칙
- `PostseasonRound` enum → 규칙셋이 주는 라운드 목록, `Bracket.Series` 에 시작 승수(어드밴티지) 필드 추가
- 시리즈 MVP: 플레이오프 MVP(+2), 한국시리즈 MVP(+4)

### 3.5 시상
| 시점 | MLB | KBO |
|---|---|---|
| 월간 | 이달의 선수·투수·루키·구원 / 마이너 이달의 선수 | 월간 MVP(타자·투수 통합 1명 + 게임용 구분: 타자/투수) / 퓨처스 이달의 선수 |
| 시즌 중 | 올스타·올스타전 MVP / 마이너 올스타 | 올스타(드림·나눔)·올스타전 MVP / 퓨처스 올스타 |
| 시즌 종료 | MVP(5)·사이영(5)·신인왕(3)·실버슬러거·골드글러브·올해의 구원(2) | MVP(5)·신인왕(3)·**골든글러브**(2, 포지션별·투수 포함, 타격 포함 종합)·**KBO 수비상**(2)·**개인 타이틀**(1씩: 타율·홈런·타점·도루·안타·출루율 / 다승·평균자책점·탈삼진·세이브) / 퓨처스 개인 타이틀 |
| 포스트시즌 | LCS MVP(2)·WS MVP(4) | PO MVP(2)·KS MVP(4) |

- 사이영상이 없는 대신 투수 골든글러브·투수 타이틀로 포인트 기대값을 MLB 와 비슷하게 맞춘다 (`AwardParityBalanceTest` 를 모드별로)
- `AwardType` 은 공통 enum 에 KBO 상을 추가하고 각 상에 모드를 둔다(수상 기록은 enum 이름으로 저장 중 → 마이그레이션 불필요). 보너스 금액은 규칙셋의 통화로

### 3.6 경제·계약 (원화)
| 항목 | KBO (설정값, 밸런스 때 조정) |
|---|---|
| 통화 표시 | `1억 5,000만원`, `3,000만원` |
| 최저 연봉 | 3,000만원 (퓨처스 포함) |
| 시장가 | 1.5억 × e^(0.09 × (OVR − 65)) → OVR 80 약 5.8억, 90 약 14억, 100 약 35억 |
| 서비스 연수 | 1군 등록일수 145일 = 1시즌 ≈ 1군 42시리즈 |
| 연봉 단계 | 신인 계약 → **연봉 협상**(비FA, 매년 구단 제시 + 협상) → FA |
| FA 자격 | 고졸 8시즌 / 대졸 7시즌, 재FA 4시즌 후 |
| 다년 계약 | 비FA 다년계약 = 기존 "연장 계약 제안" 재사용 |
| 경쟁균형세 | 구단 페이롤 상한 ≈ 115억 (MLB 사치세 기준선 자리) |
| 드래프트 | 10개 구단 × 11라운드 = 110명, 계약금 전체 1순위 5억에서 순번마다 감소, 최저 3,000만원, 전원 퓨처스 시작 |

- 금액은 지금처럼 `long` 으로 저장 (원 단위 — 100억도 범위 안). `StatFormat.money` 템플릿 호출 11곳 + 서비스 문구(`"$10,000 단위"` 등)를 규칙셋 포맷으로 교체

### 3.7 시뮬레이션 기준 기록
- **능력치 계수(`coefficients`)는 공통**: 능력치가 결과에 미치는 효과는 리그와 무관
- **리그 평균(`baseline`)만 모드별**: `game.simulation.baseline`(MLB) 옆에 `game.simulation.kbo-baseline` 추가
  - KBO 근사치(2023~2025 평균, 확정 전): 삼진 약 19%, 볼넷 약 9.5%, 몸에 맞는 공 약 1.3%, 홈런 약 2.3%, BABIP 약 .310
- `PlateAppearanceModel`·`GameSimulator` 는 규칙셋이 고른 baseline 으로 만든 인스턴스를 사용 (모드당 1개)
- 검증: `balanceTest` 에 KBO 프로필 추가 — OVR 65 가 KBO 리그 평균 기록에 근접하는지

## 4. 진행 단계

| 단계 | 내용 | 완료 기준 |
|---|---|---|
| **K1 규칙셋 분리** ✅ | `LeagueMode`·`LeagueRuleset`·`MlbRuleset` 도입, 2장 표의 하드코딩을 규칙셋 경유로 교체, V22 모드 컬럼. 동작 변화 없음 | 기존 `test`·`balanceTest`·`careerTest` 결과가 그대로 + `MlbRulesetTest` |
| **K2 전환 UX** ✅ | 모드 쿠키·헤더 탭, 선수 생성 시 모드 저장, 모드별 슬롯·대시보드·랭킹, 템플릿 금액 표시를 `@fmt.money(amount, mode)` 로 | MLB 만 있는 상태에서 탭 전환·필터 동작 |
| **K3 KBO 규칙셋** ✅ | 3장 규칙 구현 (`KboRuleset`, 일정·포스트시즌·시상·경제·드래프트) | KBO 선수로 시즌 1회 → 포스트시즌 → 다음 시즌까지 진행 |
| **K4 KBO 밸런스** ✅(검증 대기) | 기준 기록 보정, `balanceTest`·`careerTest` KBO 프로필 | 리그 평균 기록, 데뷔 시기·연봉 규모가 KBO 와 비슷 |

K1 이 가장 큰 덩어리이자 나머지의 전제다. K1 이 끝나면 K2 와 K3 은 파일이 거의 겹치지 않아 나눠서 진행할 수 있다.

## 5. K1 결과 (2026-10-24)

### 5.1 바뀐 것 — 동작은 그대로, 값은 규칙셋에서
| 영역 | 파일 | 변경 |
|---|---|---|
| 모드 | `league/LeagueMode`, `league/mode/*`, V22 | 모드 enum, 규칙셋 인터페이스·MLB 구현·정적 조회, 구단·선수·리그 월드·랭킹에 모드 컬럼 |
| 레벨 | `league/LeagueLevel` | 모드·최상위 여부, `LeagueLevel.of(mode)`. `FUTURES`·`KBO` 값은 K3 에서 추가 |
| 레벨 비교 | `AwardService`, `CareerService`, `HallOfFameService`, `ContractService`, `TeamStrengthService`, `PostseasonService`, `PlayerSeason`, `LevelChange`, `SeasonMetrics`, `SeasonService`, `SeasonSplits` | `== MLB` → `isTop()`, 고정 `LeagueLevel.MLB` → `ruleset.topLevel()` |
| 승강 | `PromotionRules`, `SeasonService` | `Params` record(주기·기준값·레전드 기준), 위·아래 레벨은 규칙셋이 준다 |
| 시즌 길이 | `PlayerSeason.applySeries`, `ContractService`, `EarningsService`, `PostseasonService`, `PostseasonController`, `SeasonTransitionService`, `AwardService` | `54`·`27`·`9`·`3` → `seriesPerSeason`·`allStarAfterSeries`·`seriesPerMonth`·`gamesInSeries` |
| 일정 | `ScheduledSeries`, `SeasonScheduleService`, `SeasonService` | 시리즈에 `games` 필드(기본 3), 일정 생성은 `ruleset.schedule(...)`, 시리즈 입력 경기 수는 일정에서 |
| 순위·지표 | `StandingsGenerator`, `StandingsService`, `LeagueStatsService`, `LeagueController`, `AdvancedStats` | 시리즈별 경기 수, 월 경기 수, 리그·지구 목록, 시즌 경기 수(포지션 보정) |
| 포스트시즌 | `RoundSpec`, `PostseasonFormat`, `MlbPostseasonFormat`, `PostseasonEngine`, `Bracket` | 라운드 목록·마지막 라운드·시리즈 MVP·시작 승수(어드밴티지)를 형식이 준다 |
| 경제·계약 | `PayrollRules`, `Payroll`, `ContractRules`, `ContractService`, `ContractController`, `WorldGenerator`, `WorldEvolution`, `LeagueWorldService`, `SeasonTransitionService`, `StatFormat` | 연봉·예산·사치세·반올림 단위·통화 표시를 `PayrollRules` 로. 서비스 연수 기준도 규칙셋 |
| 드래프트·트레이드 | `DraftRules`, `DraftService`, `TradeRules`, `TradeService` | 지명 수·계약금 곡선·시작 레벨, 트레이드 가능 레벨·고액 연봉 기준·예산 여유 단위 |

기존 static API(`Payroll.marketValue`, `PromotionRules.evaluate(level, …)`, `ContractRules.fairArbitration(…)` 등)는 MLB 값으로 위임하는 형태로 남겨 기존 테스트를 그대로 쓴다. `MlbRulesetTest` 가 MLB 규칙셋 = 분리 전 값임을 고정한다.

### 5.2 K2·K3 로 넘긴 것 (KBO 데이터가 있어야 의미가 있어서)
| 항목 | 위치 | 넘긴 단계 |
|---|---|---|
| 템플릿 금액 표시 11곳 | `@fmt.money(amount)` → `@fmt.money(amount, player.leagueMode)` | K2 ✅ |
| 브랜드 문구·헤더 탭·슬롯·대시보드·랭킹 필터 | `layout.html`, `HomeController`, `PlayerService`, `RankingService` | K2 ✅ |
| 상 목록·보너스(달러), 시상 판정 기준값(OPS .720 등) | `AwardType`, `AwardRules`, `AwardService` 의 상 선택 | K3 |
| 순위표 시드·포스트시즌 진출 규칙 | `StandingsTable.playoffSeeds` (MLB 6팀/리그) — KBO 형식은 순위표 행으로 직접 시드 | K3 |
| 시뮬레이션 baseline | `SimulationBalance`, `PlateAppearanceModel`, `NpcStatGenerator` | K3 |
| 컴바인 등급 → 라운드(20R) | `CombineGrade.roundFor` | K3 (KBO 11R) |
| 구단 재정 성향 팀 코드 | `Market.BIG_MARKET_TEAMS` 등 | K3 (KBO 코드 추가) |
| NPC 이름·외국인 슬롯 | `NameGenerator`, `WorldGenerator` | K3 |
| NPC 상태 이름 `NpcStatus.MLB` | "최상위 레벨 로스터" 의미로 그대로 사용 (DB 값이라 이름 유지) | - |

## 6. 리스크·주의
- **구단 목록 혼입**: 리그 구성원이 필요한 곳은 K1 에서 모두 모드별 조회로 바꿨다(2.2). 새 코드에서 대진·지명용으로 `findAllByOrderByIdAsc` 를 쓰지 않도록 주의
- **기존 데이터**: 모든 기존 선수·랭킹은 `DEFAULT 'MLB'` 로 자동 MLB 모드
- **상표**: 실제 KBO 구단명 사용은 MLB 와 같은 조건(학습·개인 프로젝트 범위, 공개·상업 전환 시 재검토)
- **세이버 지표 상수**: wOBA 가중치·포지션 보정은 MLB 값 기반 → KBO 는 근사로 시작하고 K4 에서 확인

## 7. 2차 대비: 국가대표 · 군 복무 (지금 만들지 않고 자리만 둔다)

### 7.1 국가대표는 모드를 가로지르는 시스템
- 대상은 **국적** 기준(`Player.nationality` 이미 있음). MLB 모드의 한국 국적 선수도 WBC·올림픽에 뽑힐 수 있다 → 규칙셋이 아니라 별도 `international` 패키지
- 대회 달력(연도별): WBC(3월, 시즌 전), 올림픽(7~8월), 아시안게임(9~10월, KBO 리그 중단), 프리미어12(11월, 포스트시즌 뒤)
- 선발 기준: 국적 내 OVR·최근 성적 순위 (NPC 비교군은 기존 리그 월드 NPC + 가상 해외파 풀)

### 7.2 군 복무는 KBO 모드 전용 규칙
- 만 27~28세 전후 미필이면 상무(퓨처스 소속팀) 입대 → 약 1.5시즌 퓨처스 기록만, FA 연수 정지
- **병역 특례**: 아시안게임 금메달, 올림픽 동메달 이상 → 국가대표 시스템과 연결되는 지점

### 7.3 1차 구조에 미리 넣어 둘 확장 지점
| 확장 지점 | 1차에서 할 일 | 2차에서 붙일 것 |
|---|---|---|
| 시리즈 후 처리 순서 | `SeasonService.playNextSeries` 의 시상 → 승강 → 트레이드를 "시리즈 후 단계" 목록으로 정리 | 대표팀 소집·병역 이벤트 단계 추가 |
| 시즌 전환 순서 | `SeasonTransitionService` 의 처리도 같은 방식으로 단계화 | 입대·전역, 국제대회(WBC·프리미어12) 단계 |
| 일정 | `ScheduledSeries` 에 경기 수 필드 추가 (2연전용) | 리그 중단(아시안게임) 표시용 이벤트 필드 |
| 규칙셋 | `hasMilitaryService()` 같은 기능 플래그 자리 (KBO true, MLB false — 1차는 미사용) | 군 복무 규칙 연결 |
| 구단 | `teams` 에 모드 컬럼 | 상무(퓨처스 전용 구단) 행 추가 |
| 선수 | (변경 없음) | `military_status`, 대표팀 이력 테이블 |

## 8. K2 결과 (2026-10-25)
| 항목 | 구현 |
|---|---|
| 모드 기억 | `ModeCookie` — 쿠키 `league_mode`(1년, HttpOnly, SameSite=Lax), 모르는 값은 MLB |
| 탭 전환 | `GET /mode/{mlb\|kbo}` (`ModeController`, 로그인 불필요) → 로그인 상태면 대시보드, 아니면 랭킹으로. redirect 파라미터 없음 |
| 화면 공통 모드 | `ModeAdvice`(@ControllerAdvice) — `currentMode`·`modes`·`currentModeAvailable`. 선수 화면·공개 프로필은 그 선수의 모드를 따르고 쿠키도 맞춘다 |
| 헤더 | 브랜드 `⚾ {모드} 선수 키우기`, 페이지 제목, `MLB \| KBO` 전환 칩. KBO 면 헤더에 `mode-kbo` → `:root:has(...)` 로 강조색(빨강) 교체 |
| 대시보드 | 탭 모드의 현역·은퇴 선수와 빈 슬롯만. 규칙이 없는 모드는 "준비 중" 안내, 생성 버튼 대신 "준비 중" |
| 선수 생성 | 탭 모드로 생성 (`PlayerService.create(userId, form, mode)`), 슬롯은 모드별 3개. 준비 중인 모드는 생성 페이지·POST 모두 대시보드로 돌려보냄 |
| 랭킹 | 탭 모드의 은퇴 선수만 (`RankingService.top(category, period, mode)`), 제목 `{모드} 랭킹` |
| 금액 표시 | 템플릿 11곳 모두 선수(랭킹은 엔트리)의 모드 통화 |

기존 API(`listActive(userId)`, `hasFreeSlot(userId)`, `create(userId, form)`, `top(category, period)`)는 MLB 기준으로 남겨 기존 테스트를 그대로 쓴다. 검증: `ModeSwitchIntegrationTest`.

## 9. K3 결과 (2026-10-25)
| 항목 | 구현 |
|---|---|
| 규칙셋 | `KboRuleset` 등록 → KBO 탭에서 선수 생성 가능 |
| 레벨 | `LeagueLevel.FUTURES`(퓨처스리그, 평균 55) → `LeagueLevel.KBO`(1군, 65) |
| 구단 | V23 시드: LG·한화·SSG·삼성·NC·KT·롯데·KIA·두산·키움 (`league='KBO'`, `division='-'`). 재정 성향: 빅 LG·삼성·SSG / 스몰 키움·NC |
| 일정 | `KboScheduleGenerator` — 1~36번 3연전, 37~54번 2연전, 상대마다 16경기·홈 8 |
| 승강 | 3시리즈마다 판정, 성적 기준 OPS .760 / 선발 ERA 4.70 / 마무리 4.40, 표본 25타석·8이닝·2이닝 |
| 포스트시즌 | `KboPostseasonFormat` — 4위 vs 5위(4위 1승 안고 최대 2경기) → 준PO(3위) → PO(2위) → 한국시리즈(1위). PO·KS MVP |
| 시상 | `AwardCatalog` 로 자리별 상 지정. KBO: 월간 MVP, 올스타·올스타전 MVP, 정규시즌 MVP, 신인상, 골든글러브(타자 포지션별·투수), KBO 수비상, 개인 타이틀 8종(리더보드 단독 1위), 퓨처스 이달의/올해의 선수·퓨처스 올스타 |
| 경제 | `PayrollRules.KBO` — 시장가 2억(OVR 65), 최저 3,000만원, 1군 1년 차부터 연봉 협상, FA 고졸 8·대졸 7년, 금액 표시 `1억 5,000만원` |
| 드래프트 | `DraftRules.KBO` — 10개 구단 × 11라운드(컴바인 등급 20R 범위를 축소), 1순위 5억, 전원 퓨처스 시작 |
| 시뮬레이션 | `game.simulation.kbo-baseline`(삼진 19%, 볼넷 9%, 홈런 2.3%, BABIP .310 근사) — 경기 엔진·NPC 기록 생성이 레벨/월드 모드로 고른다. 능력치 계수는 공통 |
| NPC | 한국 선수 한글 이름, 구단당 외국인 3명(1·2선발 + 지명타자 +4) |
| 화면 용어 | `LeagueTerms` — 메이저리그/1군, 마이너/퓨처스, 월드시리즈/한국시리즈, 연봉조정/연봉 협상. 순위표는 지구 없이 한 표 + 진출권 5팀 |

검증: `KboRulesetTest`(일정·경제·드래프트·포스트시즌 대진), `KboSeasonIntegrationTest`(KBO 월드·NPC·2연전 진행·연봉).

### 9.1 K4 로 넘긴 것 (밸런스·다듬기)
- KBO 기준 기록 확정: `balanceTest` KBO 프로필 (OVR 65 → KBO 리그 평균)
- `AwardRules`·`NpcStatGenerator` 의 MLB 기준값(OPS .720, ERA 4.40, 경기당 타석 등) 모드별화
- 명예의 전당 점수 가중치(144경기라 누적 기록이 적음), 레전드 기준 150 재검토
- 고졸 신인(OVR 30)이 퓨처스(평균 55)에서 출전 기회가 적은 문제 — 3군/육성군 없이 어떻게 키울지
- 외국인 선수 재계약·교체(현재는 시즌 전환 때 일반 NPC 처럼 노화·은퇴), 외국인 연봉 상한
- 투수 MVP(현재 MVP 판정은 타자만), 템플릿의 `/54` 표기(두 모드 모두 54라 그대로)

## 10. K4 결과 (2026-10-25)
| 항목 | 변경 |
|---|---|
| KBO 기준 기록 | BABIP .310 → .318 (계산상 1군 평균끼리 타율 .268 / 출루율 .344 / 장타율 .406). `PlateAppearanceModelTest` 에 KBO 평균 검증 추가 |
| KBO 밸런스 검증 | `KboBalanceVerificationTest`(@Tag balance) — OVR 45~85 타격, 144경기 주전 타석, 퓨처스 평균, 퓨처스 신인 출전, 선발·마무리. `SeasonHarness` 가 모드의 시리즈별 경기 수(2연전)를 따른다 |
| 커리어 검증 | `CareerBalanceTest` 에 KBO 프로필 3종(고졸 SS·대졸 SS·고졸 SP). 금액은 모드 통화, 요약은 모드별 |
| 퓨처스 출전 | `LeagueLevel.regularRating` 신설 — 입지(주전·플래툰·벤치) 기준. MLB 레벨은 평균과 같고, 퓨처스는 36 → 고졸 OVR 30 플래툰, 대졸 40 주전. 상대 전력·승강은 그대로 평균 55 |
| 시상 점수 | `AwardRules.Scoring` 모드별 (MLB OPS .720·ERA 4.40/4.00, KBO .750·4.50/4.10) |
| 승강 기준 | KBO OPS .750, 선발 ERA 4.50, 마무리 4.20 (리그 평균 보정에 맞춤) |
| 명예의 전당 | 누적 기록 항목 × 162 / 시즌 경기 수 (KBO ×1.125) — 레전드 기준 150 은 그대로 |
| 투수 MVP | KBO 는 최고 선발이면서 16승·ERA 2.50 이하이면 정규시즌 MVP |

### 10.1 다음 확인
- `gradlew balanceTest`, `gradlew careerTest -Pcareers=2` 리포트로 수치 확정 (범위 밖이면 baseline·regularRating·시장가 조정)
- 외국인 선수 재계약·교체: NPC 에 외국인 구분 컬럼이 없어 시즌이 지나면 외국인이 줄어든다 → NPC `foreign` 컬럼 + 시즌 전환 때 3명 유지 (다음 작업 후보)

## 11. 다국어(영어 화면) 연동 (2026-10-25)
- 모드 용어 `LeagueTerms` 는 한국어를 코드에, 영어를 `messages_en` 의 `terms.mlb.*` / `terms.kbo.*` 에 둔다
- 용어가 들어가는 메시지는 인자로 받는다: `career.championships({0}=횟수, {1}=우승 시리즈)`, `league.title`, `postseason.champion`, `career.totalNote`, `records.detailNote` 등
- KBO 시상(`AwardType.KBO_*`)·레벨(`LeagueLevel.FUTURES/KBO`)·라운드(`KboRound.*`) 영어 표시명은 `messages_en` 에
- 원화는 영어 화면에서 `₩150,000,000`, 한국어 화면에서 `1억 5,000만원`

## 12. K4 2차 (커리어 검증 결과 반영, 2026-10-25)
- balanceTest: KBO 주전 타석 671 — 출루율이 높아 경기당 타석이 많은 KBO 특성, 기준을 520~700 으로 수정
- careerTest: 레전드 강등 면제로 자동 진행 선수가 은퇴 조건(메이저 출전 없음)에 걸리지 않음 → 자동 은퇴 정책에 "최상위 레벨 벤치급" 추가
- KBO 연봉: 명예의 전당급 최고 연봉 중앙 14.7억·누적 108억으로 실제의 절반 → 시장가 기울기 0.075 → 0.10 (OVR 90 약 24억), 예산·경쟁균형세·고액 연봉 기준 약 30% 상향
- 결정 (2026-10-25): KBO 수상 포인트가 MLB 의 약 1.7배(개인 타이틀 +1 이 매년 쌓임)인 것은 그대로 둔다 — 경쟁자가 적고 타이틀이 많아 더 빨리 크는 것을 KBO 모드의 특징으로 본다
