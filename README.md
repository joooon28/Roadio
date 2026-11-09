# Roadio

길 위에 목소리를 남기면, 그곳을 지나는 차 안에서 다시 만나는 위치 기반 음성 커뮤니티.

Pleos Connect 차량 앱 공모전용 초기 프로젝트입니다. 사용자는 모바일에서 짧은 음성 캡슐을 만들고 위치·공개 대상을 정합니다. 발견 반경은 100m, 열람 기간은 등록 후 3일로 고정됩니다. 차량 앱에서는 주변 캡슐을 확인하고 사용자가 직접 선택해 재생합니다. 자동 재생과 주행 중 작성·상세 탐색은 MVP에 포함하지 않습니다.

현재 `main`은 Roadio 작업용입니다. 이전 DriveFit 구현과 검증 문서는 `archive/drivefit` 브랜치에 보존했습니다.

## 기술 검증 전제

- FusedLocation `1.1.3`은 Pleos AVD에서 빌드·위치 업데이트·반경 진입/이탈을 확인했습니다. 실제 차량과 개발 프로젝트 배포 권한은 미확인입니다. 상세 근거는 `DEV-02-validation.md`를 참고하세요.
- 차량 상태·주행 중 조작 허용 범위는 확인된 API와 Pleos 정책에 따라 결정합니다.
- 실제 위치 연동이 불가능한 시연에는 가상 위치와 사전 녹음 샘플을 사용하고 출처를 명시합니다.
- 사용자 수요와 사업 지표는 아직 검증되지 않았습니다.

## 모듈 실행

- 차량 시연 앱: `:app` (`com.joons.roadio`). Pleos Connect AVD에서 검증한다.
- 모바일 녹음 앱: `:mobile` (`com.joons.roadio.mobile`). 일반 Android 휴대폰 에뮬레이터에서 실행한다. `./gradlew :mobile:assembleDebug --offline --no-daemon --max-workers=2`로 모바일 앱만 빌드할 수 있다.
- 모바일 앱은 DEV-07 현재 녹음·미리 듣기까지만 동작하며 서버·차량으로 파일을 전송하지 않는다. 상세 결과는 [DEV-07 검증 기록](DEV-07-validation.md)을 참고한다.

## 와이어프레임

차량·모바일의 현재 저충실도 화면과 확인 범위는 [와이어프레임 기록](Roadio_WIREFRAME-2026-11-07.md)을 참고하세요. 웹앱의 위치·공개 설정과 로컬 저장 화면은 구현했으며, 서버 게시 연결은 아직 구현 전입니다.

## iPhone 웹앱과 네이버 지도

`web/`은 iPhone 13 mini 시연용 모바일 웹앱입니다. `python3 -m http.server 8765 --directory web`으로 로컬에서 열 수 있습니다. 마이크와 현재 위치는 HTTPS 환경 또는 `localhost`에서 사용해야 합니다.

03a 위치 선택 화면은 NAVER Maps JavaScript API를 사용합니다. NAVER Cloud의 새 **Maps** 서비스에서 애플리케이션을 만들고 **Dynamic Map**과 **Geocoding**을 사용할 수 있도록 설정한 뒤, 웹 서비스 URL에 실행 주소를 등록하세요. 로컬 테스트에는 localhost 주소, iPhone 시연에는 배포한 HTTPS 주소가 각각 필요합니다. 등록 URL의 포트 포함 여부 등은 콘솔 안내에 맞춰 확인하세요. 발급한 **Client ID**를 `web/config.js`의 `naverMapClientId`에 입력하세요. Client Secret은 브라우저 파일에 넣지 않습니다. 옛 AI NAVER API 지도 상품에는 2025년 7월부터 무료 이용량이 제공되지 않으므로 새 Maps 서비스를 선택해야 합니다.

현재 로컬 설정에는 Client ID가 입력되어 있고, localhost의 Chrome에서 지도 SDK와 Geocoding 모듈 로드 및 지도 초기화를 확인했습니다. 검색은 NAVER Geocoding의 **주소 결과**를 사용하며, 장소명·상호명 검색은 별도의 장소 검색 서비스와 서버 연동이 필요합니다. 지도 타일의 실제 표시, 검색 결과 및 iPhone Safari 동작은 아직 검증 전입니다.

홈 화면 추가용 manifest를 등록했습니다. iPhone 13 mini의 Safari 녹음·GPS·지도·홈 화면 실행은 HTTPS 배포 뒤 [DEV-08 검증 기록](DEV-08-validation.md)의 순서대로 직접 확인해야 합니다. 등록 버튼은 DEV-10 로컬 서버 주소에서 열면 서버에 게시하고, 별도 정적 서버에서 열면 IndexedDB에 **이 기기만 저장**합니다.

## DEV-09 로컬 서버

Python 3 표준 라이브러리로 만든 API가 `server/app.py`에 있습니다. 저장소는 SQLite와 로컬 음성 파일이며 `server/data/`는 Git에서 제외됩니다.

```sh
python3 server/app.py --data-dir server/data create-user '작성자'
python3 server/app.py --data-dir server/data serve
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s server -p 'test_*.py' -v
```

첫 명령은 사용자 ID와 임의 Bearer 토큰을 한 번 표시합니다. 토큰은 코드나 저장소에 넣지 마세요. 서버는 기본값 `127.0.0.1:8787`에서만 실행됩니다. API 요청/응답 형식은 [OpenAPI 계약](docs/contracts/roadio-api-v1.yaml), 검증 범위는 [DEV-09 기록](DEV-09-validation.md)을 참고하세요.

## DEV-10 모바일 웹앱 서버 연결

위 명령으로 서버를 켠 다음 `http://127.0.0.1:8787/`을 여세요. 이 주소는 웹 화면과 API를 함께 제공합니다. 등록 또는 내 캡슐 화면을 처음 열면 `create-user`에서 발급한 Bearer 토큰을 입력합니다. 등록, 내 목록 조회·재생, 공개 범위 변경, 삭제가 로컬 서버에 반영됩니다. 실패한 등록은 같은 화면에서 다시 누르면 재시도합니다. `http://localhost:8765/` 등 별도 정적 서버로 연 화면은 기존 기기 내 저장 모드이므로 서버 데이터와 자동 동기화되지 않습니다.

`내 이웃` 시연에는 별도 테스트 계정을 만들고, 당사자가 동의한 계정 ID만 작성자의 테스트 명단에 추가하세요.

```sh
python3 server/app.py --data-dir server/data create-user '이웃'
python3 server/app.py --data-dir server/data add-recipient 작성자ID 이웃ID
```

이 명단은 실제 초대·친구 기능이 아닙니다. iPhone에서의 접속과 실서비스 공유에는 HTTPS 배포, 계정 인증, 초대·동의 흐름이 필요합니다. 차량 앱의 서버 연결은 DEV-11 범위입니다. 구현·검증 근거는 [DEV-10 기록](DEV-10-validation.md)을 참고하세요.

## DEV-11 차량 앱 서버 연결

차량 앱 기본 화면에서 별도 차량 시연 계정 토큰을 입력하고 Pleos 위치 확인 또는 명시된 가상 좌표를 적용한 뒤 **주변 이야기 검색**을 누르세요. 로컬 Android 에뮬레이터의 기본 서버 주소는 `http://10.0.2.2:8787`입니다. 같은 `server/data`를 사용하는 위 로컬 서버가 켜져 있어야 합니다. 목록에서 이야기를 선택한 뒤 재생을 누를 때 서버가 권한·만료를 다시 확인합니다. 자동 재생은 없습니다. 원격 서버는 HTTPS 주소만 받습니다.

실행 중인 에뮬레이터가 없어 이번 단계의 실제 화면·음성 출력은 아직 확인 전입니다. 단계별 근거와 한계는 [DEV-11 기록](DEV-11-validation.md)에 있습니다.

## Railway HTTPS 시연 서버

저장소 루트의 `Dockerfile`은 Android Gradle 프로젝트 대신 `server/`와 `web/`만 Python 이미지로 배포한다. Railway 서비스에는 `/data` 영속 볼륨을 연결한다. 컨테이너는 Railway가 제공하는 `$PORT`와 `0.0.0.0`에서 서버를 열고, SQLite와 음성 파일은 Railway의 `RAILWAY_VOLUME_MOUNT_PATH` 또는 기본 `/data`에 저장한다. 공개 도메인은 Railway의 Public Networking에서 생성하며 별도 도메인은 필요하지 않다.

Railway `Variables`에는 서로 다른 32자 이상의 URL-safe 토큰을 비밀값으로 등록한다. 값은 Git·문서·스크린샷에 넣지 않는다.

```text
ROADIO_BOOTSTRAP_OWNER_TOKEN=<작성자용 비밀 토큰>
ROADIO_BOOTSTRAP_VEHICLE_TOKEN=<차량 청취자용 비밀 토큰>
```

선택적으로 `ROADIO_BOOTSTRAP_OWNER_NAME`, `ROADIO_BOOTSTRAP_VEHICLE_NAME`을 지정할 수 있다. 서버는 시작할 때 안정적인 시연 사용자 ID 두 개와 작성자→차량 수신자 관계를 중복 없이 준비하고 토큰 원문은 저장하지 않는다. 토큰을 변경하면 다음 시작부터 저장된 해시가 바뀐다. `/v1/health`를 Railway Healthcheck Path로 설정하고, 배포 뒤 HTTPS 주소에서 상태 응답을 확인한다.

이 구성은 공모전 시연용이다. 실제 로그인·초대 동의·비밀값 회전 UI·관리자 복구 기능을 대신하지 않는다. 장기 운영에서는 SQLite와 로컬 볼륨 대신 관리형 DB와 오브젝트 스토리지 분리를 검토한다.
