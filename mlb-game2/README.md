# MLB 선수 키우기

나만의 야구 선수를 드래프트 컴바인부터 은퇴까지 키우는 웹 게임. 요구사항은 [docs/requirements.md](docs/requirements.md) 참고.

## 환경
- Java 21, Spring Boot 3.5, Gradle 8.14 (wrapper 포함)
- MySQL 8 (Flyway 로 스키마 관리)
- 화면: Thymeleaf + 바닐라 JS

## 로컬 실행
1. DB 생성 — MySQL 에 관리자 계정으로 [scripts/create-database.sql](scripts/create-database.sql) 실행 (비밀번호는 바꿔서 사용)
2. 접속 정보 기입 — `src/main/resources/application-local.yml` 의 서버 주소·계정·비밀번호 빈칸 채우기
   (git 에서 제외되는 파일. 새로 받은 경우 `application-local.yml.example` 을 복사)
3. 실행 (JAVA_HOME 이 JDK 21 이어야 함)
   ```
   gradlew.bat bootRun
   ```
4. 접속: http://localhost:8080/game — 메인 주소는 `/game` (context-path), 루트 `/` 는 사용하지 않음

## 주요 경로 (모두 `/game` 하위)
| 경로 | 설명 | 로그인 필요 |
|---|---|---|
| `/game/` | 대시보드로 이동 | O |
| `/game/login` | 로그인 (POST 도 동일 경로) | X |
| `/game/signup` | 회원가입 | X |
| `/game/logout` | 로그아웃 (POST) | O |
| `/game/dashboard` | 메인 대시보드 (선수 슬롯 3개) | O |
| `/game/players/new` | 선수 생성 (POST `/game/players`) | O |
| `/game/players/{id}` | 선수 정보 (능력치, OVR) | O |
| `/game/players/{id}/combine` | 컴바인 안내·인터뷰 / 결과 (POST 로 진행, 1회) | O |
| `/game/players/{id}/draft` | 드래프트 결과 (POST 로 진행, 1회) | O |
| `/game/players/{id}/season` | 시즌 허브: 선수 상태, 다음 상대, 시리즈 진행·훈련 | O |
| `/game/players/{id}/season/play` | 다음 시리즈 진행 (POST, 훈련을 마친 뒤에만) | O |
| `/game/players/{id}/season/train` | 훈련: 능력치 하나 +1 (POST `attribute=CONTACT` 등) | O |
| `/game/players/{id}/season/series/{no}` | 시리즈 결과: 경기별 점수·이닝 득점·내 기록·하이라이트 (시드로 재계산) | O |
| `/game/players/{id}/season/stats` | 시즌 누적 기록, 시리즈 기록, 훈련 기록, 커리어 수령액 | O |
| `/game/css/**` | 정적 리소스 | X |

선수 관련 경로는 본인 선수만 접근할 수 있고, 남의 선수는 404를 돌려줍니다.

## 테스트
```
gradlew.bat test
```
- `AuthIntegrationTest` — H2(MySQL 모드)로 회원가입·로그인 흐름 검증
- `PlayerFlowIntegrationTest` — 랜덤을 고정한 상태로 선수 생성 → 컴바인 → 드래프트 흐름, 소유권, 슬롯 제한 검증
- `PlayerTest`, `CombineRulesTest` — 시작 능력치·OVR, 등급별 라운드, 계약금, 기록 환산 단위 테스트
- `MySqlMigrationTest` — Testcontainers 로 실제 MySQL 8 에서 Flyway + 엔티티 매핑 검증 (Docker 미실행 시 자동 건너뜀)
- `simulation.*` — 타석 모델, 경기 진행, 출전·부상 규칙, 시리즈·시드 재현 단위 테스트

### 밸런스 검증 (시간이 오래 걸려 일반 test 에서 분리)
```
gradlew.bat balanceTest
```
시즌을 수백 번 반복해 OVR별 시즌 기록이 실제 MLB 분포와 비슷한지 검증하고, 리포트 표를 콘솔에 출력합니다. 설계: [docs/simulation.md](docs/simulation.md)

### 장기 커리어 검증 (수십 분 걸릴 수 있음)
```
gradlew.bat careerTest -Pcareers=2
```
프로필 6종(고졸/대졸 SS, 대졸 OF, 고졸/대졸 SP, 대졸 CP)을 프로필당 N명씩 생성부터 은퇴까지 실제 서비스 흐름으로 자동 진행합니다.
- 훈련·스탯업 포인트는 균형형으로 자동 배분합니다.
- 계약은 연장 제안을 먼저 수락하고, 없으면 총액이 가장 큰 제안을 수락합니다.
- 35세 이상이고 그 시즌 메이저 출전이 없으면 스스로 은퇴합니다.

끝나면 커리어별·프로필별로 메이저 데뷔 나이, 수상 빈도, 연봉 규모, 은퇴 나이 리포트를 출력합니다.

## 패키지 구조
```
com.mlbgame
 ├─ auth     회원가입·로그인 (User, AuthService, AuthController, AuthUser)
 ├─ home     대시보드
 ├─ player   선수 생성·조회, 능력치(Attribute), OVR
 ├─ combine  드래프트 컴바인·드래프트
 ├─ league   MLB 30개 구단(Team), 리그 레벨
 ├─ simulation  즉시 결과 엔진: 타석 모델 → 경기 → 출전·부상 규칙 → 시리즈 (DB 의존 없음)
 ├─ league.world  리그 월드: 구단 성향·NPC 선수·연봉, 팀 전력 계산
 ├─ season   시즌 진행(SeasonService), 일정, 시리즈 기록·시드 재현, 수령 원장
 ├─ training 훈련 (능력치 +1, 훈련 기록)
 ├─ config   Security, JPA, 밸런스 수치(GameBalance)
 └─ common   공통 엔티티, 랜덤(Dice)
```

밸런스 수치(시작 능력치, 슬롯 수 등)는 `application.yml` 의 `game.balance` 에서 조정합니다.
