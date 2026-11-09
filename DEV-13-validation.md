# DEV-13 모바일→서버→차량 통합 시연·제출 준비

- 날짜: 2026-11-09
- 상태: **Railway 배포 설정 구현·로컬 서버 검증 완료, GitHub 푸시와 Railway 재배포 대기**

## 작업 목표·근거

Vercel 정적 웹앱과 Mac 로컬 서버를 분리한 상태에서는 iPhone에서 등록한 동일 캡슐을 Pleos AVD에서 찾을 수 없다. 두 앱이 함께 접근하는 HTTPS 서버와 재배포 뒤에도 SQLite·음성 파일이 남는 저장소를 마련하고, 실제 모바일 등록→차량 검색→사용자 선택 재생을 끝까지 검증한다.

## 할 일·완료 기준

- [x] 첫 Railway 자동 빌드 실패 화면과 확인 가능한 원인 범위 기록
- [x] Android Gradle 자동 감지를 피하는 Python 전용 Docker 이미지 구성
- [x] Railway `$PORT`, 공개 바인딩, `/data` 영속 경로 연결 준비
- [x] 환경변수 기반 작성자·차량 계정 초기화와 토큰 해시 저장
- [x] 서버 자동 테스트·문법·공개 바인딩·계정 인증 로컬 검증
- [ ] 배포 설정을 GitHub `main`에 푸시하고 Railway 이미지 빌드 성공 확인
- [ ] Railway `/data` 영속 볼륨 연결과 재배포 후 데이터 보존 확인
- [ ] Railway 비밀 환경변수와 `/v1/health` 상태 확인 설정
- [ ] Railway 공개 HTTPS 도메인 생성과 NAVER Maps 허용 URL 등록
- [ ] iPhone HTTPS 웹앱 등록→Pleos AVD 검색→선택 재생 확인
- [ ] 삭제·만료·비허용 계정과 네트워크 실패 복구 확인
- [ ] 실제 결과·화면·로그·출처·비용과 한계를 제출 자료에 연결

**완료 기준:** iPhone HTTPS 웹앱에서 새로 녹음·등록한 캡슐이 같은 Railway 데이터 저장소를 통해 Pleos AVD에 나타나며, 사용자가 선택한 뒤 재생된다. 삭제·만료·접근 제한이 서버에서 반영되고 실제 차량·주행 중 동작 등 미검증 항목은 분리한다.

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

## 포트폴리오 관점의 결정·증거

- **문제와 근거:** 모바일·차량 화면을 각각 구현해도 같은 원격 저장소를 공유하지 않으면 핵심 가치인 장소 기반 비동기 음성 전달을 증명할 수 없다.
- **고려한 대안:** Railway의 자동 감지를 유지하며 빌드 설정만 덮는 방식보다 Dockerfile로 포함 파일과 런타임을 명확히 했다. Supabase·오브젝트 스토리지 분리는 장기 운영 대안이지만 이번 통합 시연에는 변경 범위가 크다.
- **선택 이유·절충:** SQLite와 음성을 단일 볼륨에 유지해 기존 서버 계약을 보존한다. 단일 인스턴스·볼륨 의존 구조는 확장성과 장애 복구에 한계가 있으며 장기 사업 구조로 제시하지 않는다.
- **사용자 검증 방법:** iPhone 작성자가 녹음·위치·공개 대상을 확인해 등록하고, 차량 사용자가 같은 장소에서 목록을 찾아 출처를 이해한 뒤 명시적으로 재생하는 과업을 관찰한다. 성공률·만족도는 관찰 전이며 만들지 않는다.
- **현재 결과와 한계:** 코드·로컬 서버 검증만 완료했다. Railway 이미지 빌드, 볼륨, HTTPS, iPhone·Pleos 통합은 아직 결과가 없다.

## 다음 작업

1. 구현·문서 파일만 커밋하고 GitHub `main`에 푸시한다.
2. Railway 자동 재배포에서 `Using detected Dockerfile`과 이미지 빌드 성공을 확인한다.
3. 서비스에 `/data` 볼륨, 두 비밀 토큰, Healthcheck Path `/v1/health`를 설정한다.
4. 공개 HTTPS 도메인을 만든 뒤 health 응답과 재시작 후 DB 보존을 확인한다.
5. NAVER Maps 웹 서비스 URL을 추가하고 iPhone→Pleos 통합 과업을 실행한다.
