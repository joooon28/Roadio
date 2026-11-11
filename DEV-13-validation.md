# DEV-13 모바일→서버→차량 통합 시연·제출 준비

- 날짜: 2026-11-09
- 상태: **핵심 통합 검증 완료. iPhone 등록→Railway HTTPS→Pleos AVD 검색·선택 재생 확인**

## 작업 목표·근거

Vercel 정적 웹앱과 Mac 로컬 서버를 분리한 상태에서는 iPhone에서 등록한 동일 캡슐을 Pleos AVD에서 찾을 수 없다. 두 앱이 함께 접근하는 HTTPS 서버와 재배포 뒤에도 SQLite·음성 파일이 남는 저장소를 마련하고, 실제 모바일 등록→차량 검색→사용자 선택 재생을 끝까지 검증한다.

## 할 일·완료 기준

- [x] 첫 Railway 자동 빌드 실패 화면과 확인 가능한 원인 범위 기록
- [x] Android Gradle 자동 감지를 피하는 Python 전용 Docker 이미지 구성
- [x] Railway `$PORT`, 공개 바인딩, `/data` 영속 경로 연결 준비
- [x] 환경변수 기반 작성자·차량 계정 초기화와 토큰 해시 저장
- [x] 서버 자동 테스트·문법·공개 바인딩·계정 인증 로컬 검증
- [x] 배포 설정을 GitHub `main`에 푸시하고 Railway 이미지 빌드 성공 확인
- [x] Railway `/data` 영속 볼륨 연결 상태 확인
- [x] 재배포 후 SQLite·음성 데이터 보존 확인
- [x] Railway 비밀 환경변수 적용과 공개 `/v1/health` 응답 확인
- [x] Railway Healthcheck Path `/v1/health` 설정·배포
- [x] Railway 공개 HTTPS 도메인 생성
- [x] NAVER Maps 허용 URL 등록
- [x] iPhone HTTPS 웹앱 등록→Pleos AVD 검색→선택 재생 확인
- [x] `SELF` 검색 제외, `SELECTED` 전환 후 노출, 삭제 후 검색 제외 확인
- [x] 만료·비허용 계정의 검색·음성 접근 제한을 서버 자동 테스트로 확인
- [ ] 배포 환경에서 만료·비허용 계정과 네트워크 실패 복구 수동 검증
- [ ] 실제 결과·화면·로그·출처·비용과 한계를 제출 자료에 연결

**핵심 통합 완료 기준:** iPhone HTTPS 웹앱에서 새로 녹음·등록한 캡슐이 같은 Railway 데이터 저장소를 통해 Pleos AVD에 나타나며, 사용자가 선택한 뒤 재생된다. 같은 통합 흐름에서 공개 범위 변경과 삭제의 반영을 확인한다. 만료·비허용 계정은 자동 테스트와 배포 환경 수동 검증을 구분한다.

## 2026-11-09 첫 Railway 빌드 실패

- **관찰된 사실:** 사용자가 Railway 계정과 GitHub 프로젝트를 먼저 만들자 최신 GitHub 커밋 `Implement DEV-11 vehicle server discovery...`의 자동 배포가 시작됐다. Initialization 뒤 `Build › Build image` 단계에서 약 1분 41초 만에 실패했고 Deploy는 시작되지 않았다.
- **로그 화면:** Railpack/mise가 저장소 루트의 Android 프로젝트를 감지해 `gradle@9.8.2`, `java@21.0.13+9.0.LTS` 설치를 시도했다. 화면 상단에는 Railway가 외부 네트워크 차단 원인을 파악해 수정 중이라는 서비스 공지도 동시에 표시됐다.
- **판단 경계:** 저장소에 Python 서버용 배포 설정이 없었던 것은 재현 가능한 구성 문제다. 외부 네트워크 장애가 도구 다운로드 실패에 영향을 줬을 가능성도 있지만 전체 로그를 확보하지 않았으므로 단독 원인으로 확정하지 않는다. 이 배포는 서버 실행이나 HTTPS 검증까지 도달하지 않았다.
- **증거:** `docs/evidence/roadio-railway-first-build-failed.png`. 화면에 결제정보·토큰은 없으며 Railway 프로젝트·서비스 식별 URL 일부는 브라우저 주소창에 보인다. 공개 포트폴리오에 사용할 때는 식별자를 가린 사본을 만든다.

## 구현 결정과 결과

1. 저장소 루트에 `Dockerfile`을 추가해 Railway가 Gradle 자동 감지 대신 Python 3.12 이미지를 사용하도록 했다. 이미지는 `server/`와 `web/`만 복사하고 `.dockerignore`로 Android·Gradle·IDE·문서·로컬 데이터를 빌드 컨텍스트에서 제외한다.
2. 컨테이너는 Railway가 주입하는 `$PORT`와 `0.0.0.0`에서 실행한다. 외부 바인딩은 `ROADIO_ALLOW_PUBLIC_BIND=1`일 때만 허용해 로컬 기본값 `127.0.0.1`을 유지한다.
3. 데이터 경로는 Railway의 `RAILWAY_VOLUME_MOUNT_PATH`를 우선하고 없으면 `/data`를 사용한다. Railway 서비스에 `/data` 볼륨을 연결하기 전에는 파일이 영속된다고 표현하지 않는다.
4. `ROADIO_BOOTSTRAP_OWNER_TOKEN`, `ROADIO_BOOTSTRAP_VEHICLE_TOKEN`이 모두 설정된 경우 안정적인 사용자 ID 두 개와 작성자→차량 수신자 관계를 중복 없이 만든다. 토큰은 32~128자의 URL-safe 문자열이어야 하고 서로 달라야 한다. DB에는 SHA-256 해시만 저장하고 로그에는 토큰을 출력하지 않는다.
5. `/v1/health`는 Docker 환경에서 `railway-demo` 모드를 돌려주도록 했다. 이는 Railway 상태 확인용 표기이며 운영 서비스 인증을 뜻하지 않는다.

## 검증

- `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s server -p 'test_*.py' -v`: 기존 API와 신규 배포 계정 테스트 총 6개 통과.
- `python3 -m py_compile server/app.py server/test_app.py`: 통과.
- 공개 바인딩과 임시 데이터 경로로 서버 실행: 시작 로그에 토큰 없이 두 시연 사용자 ID만 표시됨.
- 작성자·차량 토큰으로 인증 API 각각 HTTP 200, 작성자 수신자 목록에 차량 사용자 1명 확인.
- `git diff --check`: 통과.
- Docker CLI는 설치돼 있지만 로컬 Docker 데몬이 꺼져 있어 `docker build`는 실행 전에 중단됐다. 따라서 Docker 이미지 자체의 성공은 Railway 재배포에서 확인해야 한다.

## GitHub 푸시와 Railway 재배포 결과

- **커밋·푸시:** 배포 코드·테스트·DEV 문서·증거만 선별해 `5d1bc33 Prepare Railway HTTPS deployment`로 커밋하고 GitHub `main`에 푸시했다. 기존 `.idea`와 별도 디자인 문서는 커밋하지 않았다.
- **이미지 빌드:** Railway가 새 커밋을 자동 감지했다. Build Logs에서 `load build definition from Dockerfile`, `python:3.12-slim`, `COPY server`, `COPY web`을 확인해 Gradle·Java 자동 감지가 제거됐음을 검증했다. `.dockerignore` 적용 뒤 업로드된 스냅샷은 약 4.8MB였고 이미지 푸시는 약 42.5MB였다.
- **배포:** Railway 화면이 `Online`, 배포가 `ACTIVE / Deployment successful`로 전환됐다. Deploy Logs에서 컨테이너 시작과 `Roadio API at http://0.0.0.0:8080 (railway-demo)`를 확인했다. 이 배포 시점의 서비스는 `Unexposed service` 상태였다.
- **판단:** 이 단계에서 Docker 이미지 빌드와 런타임 시작을 확인했다. `/data` 볼륨과 부트스트랩 비밀값은 후속 배포에서 적용했다.

## Railway 볼륨 설정과 불필요 서비스 확인

- **관찰된 화면:** Roadio에 Mount Path `/data`, 최대 크기 5.00GB인 `roadio-volume`이 추가됐지만 상단에 `Apply 5 changes`가 남아 있다. 따라서 볼륨은 설정 대기 상태이며 실제 서비스에 적용·마운트됐다고 아직 판단하지 않는다.
- **용량·비용:** Railway 공식 Volumes 문서 기준 Hobby 플랜의 기본/최대 볼륨 크기는 5GB이고 현재 축소는 지원하지 않는다. 5GB 전체가 선결제되는 방식이 아니라 실제 저장 사용량에 대해 월 GB당 과금된다. 메타데이터가 약 2~3%를 사용할 수 있다. 확인일 2026-11-09: https://docs.railway.com/volumes/reference
- **불필요 리소스:** 같은 변경 묶음에 `function-bun` 서비스가 새로 생성돼 있다. Railway Function은 Bun에서 단일 TypeScript 파일을 실행하는 웹훅·크론·간단 API용 별도 서비스이며, Python `Roadio` 서버가 이미 API를 제공하므로 현재 구조에는 필요하지 않다. 확인일 2026-11-09: https://docs.railway.com/functions
- **다음 조치:** `function-bun`을 변경 묶음에서 제거한 다음 Roadio와 `/data` 볼륨만 적용한다. 적용 전에는 재배포나 영속성 성공으로 기록하지 않는다.
- **후속 확인:** 사용자가 `function-bun`을 삭제한 뒤 프로젝트 캔버스에는 `Roadio Online`과 연결된 `roadio-volume`만 남았고 `Apply changes` 버튼은 표시되지 않았다. 볼륨 리소스 연결은 완료로 바꾸되, SQLite 행·음성 파일을 만든 뒤 재배포하는 영속성 검증은 별도 미완료 상태로 유지한다.

## 비밀 변수 적용·재배포 결과

- **설정:** `ROADIO_BOOTSTRAP_OWNER_TOKEN`, `ROADIO_BOOTSTRAP_VEHICLE_TOKEN`에 서로 다른 64자 URL-safe 난수를 등록했다. 실제 값은 채팅 출력·문서·Git에 남기지 않았고 Railway 화면에서도 마스킹됨을 확인했다.
- **배포 상태:** 사용자가 두 변수 변경을 Deploy했고 배포 `1e78420a`가 `ACTIVE / Deployment successful`이 됐다.
- **볼륨 증거:** Deploy Logs에서 컨테이너 시작 전에 볼륨 마운트 로그를 확인했다. 호스트 내부 경로는 Railway 구현 세부사항이므로 앱 계약에는 사용하지 않으며 서버는 설정된 `/data` 경로를 사용한다.
- **계정 초기화:** 같은 로그에서 `bootstrapUsersReady: true`와 작성자·차량 사용자 ID가 각각 생성됐음을 확인했다. 토큰 원문은 로그에 나타나지 않았다.
- **서버 시작:** `Roadio API at http://0.0.0.0:8080 (railway-demo)`를 확인했다.
- **판단 경계:** 볼륨 마운트와 초기 데이터 생성은 확인했지만 재배포 뒤 같은 DB 데이터가 남는지는 아직 시험하지 않았다. 서비스는 여전히 `Unexposed service`라 외부 `/v1/health`와 iPhone·Pleos 접근도 미검증이다.

## 공개 HTTPS 도메인·health 확인

- **도메인:** Railway Public Networking에서 `https://roadio-production.up.railway.app`을 포트 8080 대상으로 생성했다.
- **외부 검증:** 별도 브라우저 탭에서 `https://roadio-production.up.railway.app/v1/health`에 접속해 `{"service":"roadio","mode":"railway-demo"}` 응답을 확인했다. 따라서 공개 DNS·TLS·Railway 라우팅·서버 응답은 동작한다.
- **남은 설정:** Railway 배포 자체의 Healthcheck Path 입력란은 아직 비어 있으므로 `/v1/health`로 별도 설정해야 한다. NAVER Maps 허용 URL과 모바일·차량 앱의 서버 주소도 아직 변경하지 않았다.
- **판단 경계:** health 응답은 서버 가동 증거일 뿐 계정 인증, 데이터 게시·검색, 재배포 후 파일 보존이나 실제 기기 통합 성공을 증명하지 않는다.
- **Healthcheck 적용:** Railway Settings의 Healthcheck Path에 `/v1/health`를 저장하고 재배포했다. 배포는 약 1분 뒤 `ACTIVE / Deployment successful`로 전환됐다. 공개 health 응답은 앞서 확인했지만 Railway 내부 probe의 개별 요청 로그는 별도로 확보하지 않았으므로 Active 상태와 설정 적용을 근거로 기록한다.

## NAVER Maps 허용 URL·공개 웹 확인

- **사용자 설정:** NAVER Cloud Platform의 Roadio Maps Application Web 서비스 URL에 `https://roadio-production.up.railway.app`을 등록하고 저장했다.
- **공개 화면:** 별도 브라우저에서 Railway 루트 주소를 열어 Roadio 제목과 `이야기 녹음` 홈 화면이 HTTPS로 렌더링됨을 확인했다. 초기 로드의 브라우저 console warning/error는 관찰되지 않았다.
- **판단 경계:** 홈 화면은 지도를 아직 초기화하지 않는다. 이 화면에서 확인한 범위는 홈 렌더링이다. 위치 권한 승인, NAVER 지도 타일, Geocoding·Reverse Geocoding은 별도 검증 범위다.
- **실기기에서 발견한 문구 문제:** iPhone에서 게시를 시도했을 때 인증 prompt가 `로컬 개발 서버의 Bearer 토큰`을 요구해 현재 Railway 환경과 사용자 용어에 맞지 않았다. 동작상 필요한 값은 작성자용 시연 토큰이므로 `Roadio 시연 계정 연결 코드`로 바꾸고 JavaScript 캐시 버전을 갱신했다. 인증 자체를 제거하거나 비밀값을 웹 코드에 넣지 않는다.

## 최종 통합 검증 (2026-11-09)

공모전 프로젝트의 보조 기록 `Roadio_DEV-13-final-validation-2026-11-09.md`에 남긴 실행 결과다. iPhone Roadio 웹앱의 Railway 작성자 시연 계정과 Pleos AVD 차량 앱의 Railway 차량 시연 계정을 같은 공개 HTTPS 서버 및 `/data` 볼륨에 연결했다. AVD에는 최신 캡슐과 일치하는 직접 입력 가상 위치를 사용했다.

| 검증 항목 | 관찰 결과 |
| --- | --- |
| iPhone 등록·조회 | iPhone에서 녹음 후 `서버에 등록되었어요`를 확인했다. 작성자 연결 코드를 다시 입력하자 `내 이야기`에 새 항목이 나타났다. |
| 재배포 후 영속성 | Railway 컨테이너를 재배포하고 다시 인증한 뒤 같은 항목이 남았다. 재배포 뒤 iPhone에서 원래 녹음이 재생된다는 사용자 확인을 받아 SQLite 데이터와 업로드 음성의 보존을 확인했다. |
| 공개 범위 변경 | `나에게만 들려주기(SELF)`일 때 차량 검색 결과는 0개였다. iPhone에서 `내 이웃(SELECTED)`으로 바꾸자 같은 가상 위치에서 해당 캡슐이 나타났다. 작성자→차량 시연 계정의 수신자 관계가 서버 판정에 반영됐다. |
| 선택 재생 | Pleos AVD 상세에서 사용자가 `선택 재생`을 눌렀고, iPhone에서 녹음한 음성이 들렸다고 확인했다. 청취 근거는 사용자 보고다. |
| 삭제 반영 | 같은 위치와 `내 이웃` 범위에 삭제 검증용 캡슐을 추가하자 차량 검색 결과가 2개가 됐다. iPhone에서 해당 항목만 삭제한 뒤 AVD에서 다시 검색하자 기존 1개만 남았다. |

- **대안과 선택:** `모두에게(PUBLIC)`로 검색만 확인하는 방법도 있었지만, 수신자 관계까지 검증하기 위해 `내 이웃(SELECTED)`을 사용했다.
- **검증 환경의 제약:** 자동화 환경의 ADB 소켓과 직접 외부 API 요청 제한으로 사용자 실행 AVD와 Railway Console의 읽기 전용 DB 확인을 조합했다. 차량 연결 코드는 가상 키패드 대신 Android Studio Terminal의 ADB 텍스트 입력으로 전달했다. 토큰 원문과 가상 좌표는 기록에서 제외했다.
- **자동 테스트 근거:** [서버 테스트](server/test_app.py)의 `test_visibility_radius_expiry_delete_and_range`는 만료 또는 수신자 미지정 시 해당 캡슐의 검색 제외와 음성 접근 HTTP 404를 확인한다. 배포 환경의 iPhone→Pleos AVD 흐름에서 만료, 별도 비허용 계정, 네트워크 전환·장애 복구는 수동 미검증이다.
- **실차 검증 범위:** 직접 입력 가상 위치를 사용했으므로 Pleos 위치 SDK와 실차 GPS는 미검증이다. 실차 오디오, Pleos 시스템 미디어 표면, 주행 중 조작 제한도 후속 검증 범위다.

## 포트폴리오 관점의 결정·증거

- **문제와 근거:** 모바일·차량 화면을 각각 구현해도 같은 원격 저장소를 공유하지 않으면 핵심 가치인 장소 기반 비동기 음성 전달을 증명할 수 없다.
- **고려한 대안:** Railway의 자동 감지를 유지하며 빌드 설정만 덮는 방식보다 Dockerfile로 포함 파일과 런타임을 명확히 했다. Supabase·오브젝트 스토리지 분리는 장기 운영 대안이지만 이번 통합 시연에는 변경 범위가 크다.
- **선택 이유·절충:** SQLite와 음성을 단일 볼륨에 유지해 기존 서버 계약을 보존한다. 단일 인스턴스·볼륨 의존 구조는 확장성과 장애 복구에 한계가 있으며 장기 사업 구조로 제시하지 않는다.
- **사용자 검증 방법:** iPhone 작성자가 녹음·위치·공개 대상을 확인해 등록하고, 차량 사용자가 같은 장소에서 목록을 찾아 출처를 이해한 뒤 명시적으로 재생하는 과업을 관찰한다. 성공률·만족도는 관찰 전이며 만들지 않는다.
- **실제 결과와 한계:** 공용 HTTPS에서 같은 녹음의 등록·검색·선택 재생과 삭제 반영을 확인했다. 시연 계정은 실제 로그인·초대·동의 UX 검증 범위와 분리한다.

## 남은 작업

1. 배포 환경에서 만료·비허용 계정과 네트워크 전환·장애 후 재시도를 확인한다.
2. 공식 권한·정책을 확인한 뒤 Pleos SDK 위치, 실차 오디오와 주행 중 동작을 별도 검증한다.
3. 공모전 제출 자료에 검증 범위와 실제 결과, 화면·로그·출처·비용 근거를 연결한다.

## 모바일 웹앱 시연 계정 연결 유지 (2026-11-10)

- **시연 절차:** iPhone 홈 화면에서 Roadio를 열고 이야기 등록 또는 `내 이야기`를 처음 이용할 때 작성자용 연결 코드를 한 번 입력한다. 두 기능은 같은 세션을 사용한다. 앱을 닫았다가 열어도 브라우저에 유효한 세션이 남아 있으면 추가 입력 없이 이용할 수 있다.
- **유지 정책:** 최초 구현은 7일이었고, 촬영부터 공모전 심사·전시까지 이어서 쓰도록 180일로 늘렸다. 만료까지 7일 이하가 남은 유효한 세션은 웹앱을 다시 열고 등록 또는 조회로 연결을 확인할 때 180일로 갱신된다. 배포 전 만든 유효한 7일 세션도 이 규칙으로 갱신된다. 세션 만료, 웹앱 데이터 삭제 또는 서버 연결 코드 교체 후에는 다시 연결해야 한다.
- **구현:** `/v1/web-session`은 입력한 코드를 확인한 뒤 별도의 세션 ID를 발급한다. Railway HTTPS에서는 `Secure`, `HttpOnly`, `SameSite=Strict` 쿠키로 전달하고 서버는 SQLite에 세션 ID의 해시와 만료 시각을 저장한다. 작성자용 코드는 공개 웹 파일에 포함하지 않는다. 쿠키 설정 근거는 [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet)에서 2026-11-10 확인했다.
- **검증 결과:** 서버 테스트 8개가 통과했다. 같은 세션으로 등록·조회, 기존 7일 세션의 180일 갱신, 만료 후 접근 거부를 확인했다. 웹 코드 테스트에서는 다시 열었을 때 저장된 세션으로 입력창을 건너뛰는 흐름을 확인했다. 최초 구현 `f83be48`, 기간 변경 `ca6e700`이 Railway에 배포됐다.
- **남은 실기기 확인:** iPhone 홈 화면 앱에서 첫 코드 입력 후 이야기를 등록한다. 앱을 종료하고 홈 화면 아이콘으로 다시 열어 `내 이야기`에 코드 요청 없이 같은 항목이 표시되는지 확인한다.
