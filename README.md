# Roadio

혼자 운전하는 순간, 익숙한 사람의 경험과 장소별 주행 팁을 필요한 위치에서 목소리로 발견하는 위치 기반 차량 오디오 서비스입니다.

Roadio는 모바일 웹앱에서 특정 위치에 `이야기` 또는 `주행 팁` 음성을 남기고, 차량이 해당 위치에 진입하면 Pleos Connect 앱에서 발견·청취하는 서비스입니다. 모바일은 녹음·위치·오디오 유형·공개 범위 설정을 담당하고, 차량 앱은 위치 기반 자동 발견과 선택 재생에 집중합니다.

## 공모전 제출 정보

- 제출자: 김준수
- 앱 이름: Roadio
- Android 패키지: `com.joons.roadio`
- 앱 버전: `1.0` (`versionCode 1`)
- 제출 APK: [`apk/김준수_Roadio_release.apk`](apk/%EA%B9%80%EC%A4%80%EC%88%98_Roadio_release.apk)
- 모바일 웹앱: [https://roadio-production.up.railway.app/](https://roadio-production.up.railway.app/)
- APK SHA-256: `24e95257ef1965be0ac2f06e4e92e2e88c4171634c7a14992017f6624f47967a`

## 주요 기능

### 모바일 작성 웹앱

- 최대 30초 음성 녹음, 미리 듣기, 다시 녹음
- NAVER Maps를 이용한 등록 위치 선택
- `이야기`와 `주행 팁` 오디오 유형 설정
- 제목과 공개 범위 설정
- 등록한 오디오의 목록·지도 확인 및 관리

### Pleos Connect 차량 앱

- FusedLocation SDK를 이용한 차량 위치 수신
- 등록 반경 진입 시 서버의 음성 자동 검색
- 오디오 유형별 발견 알림음과 발견 화면
- `음성 듣기` 또는 `건너뛰기` 선택
- 재생·일시정지·다시 듣기·재생 위치 이동
- CarUxRestrictions 상태에 따른 화면 조작 제한

### Roadio 서버

- Railway HTTPS 배포
- 음원과 메타데이터 저장
- 모바일 작성자와 차량 청취 계정 인증
- 거리·공개 범위·청취 권한·유효기간·삭제 상태 검증
- 음원 요청 시 접근 권한 재검증

## 테스트 환경

- Pleos Connect: `v2.1.0`
- Android: API 34
- 아키텍처: `arm64-v8a`
- 화면 규격: `2560×1440`
- 화면 밀도: `160dpi`
- 위치 SDK: Pleos FusedLocation `1.1.3`
- 차량 앱: Kotlin, Jetpack Compose, Android Media3
- 서버: Railway HTTPS, SQLite, 영속 볼륨
- 모바일 확인 환경: iPhone Safari 홈 화면 웹앱

## 제출 APK 설치

Pleos Connect 에뮬레이터를 실행한 뒤 Android SDK의 ADB로 APK를 설치합니다.

```sh
adb install -r apk/김준수_Roadio_release.apk
```

기존에 다른 서명의 디버그 APK가 설치돼 서명 충돌이 발생하면 기존 `com.joons.roadio` 앱을 제거한 뒤 다시 설치합니다.

제출용 Release APK에는 심사용 차량 연결 코드가 마스킹된 상태로 자동 입력됩니다. 심사위원은 코드를 직접 입력할 필요가 없습니다. 해당 코드는 심사 종료 후 폐기합니다.

## 심사용 시연 절차

사전에 등록한 심사용 오디오는 `[심사용] 합류시 사각지대 확인`입니다. 등록 반경은 100m입니다.

1. Pleos Connect 에뮬레이터 오른쪽의 `…` 메뉴에서 `Location`을 엽니다.
2. `Single points`에서 반경 밖 시작 좌표를 선택하고 `SET LOCATION`을 누릅니다.
   - 위도: `37.5820`
   - 경도: `126.9950`
3. Roadio를 실행합니다.
4. 심사용 연결 코드가 자동 입력된 것을 확인합니다.
5. 위치 권한을 요청하면 허용하고, `Pleos 위치 확인 완료`를 확인한 뒤 `시연 시작`을 누릅니다.
6. 에뮬레이터의 `Location`으로 돌아가 이야기 등록 좌표를 선택하고 `SET LOCATION`을 누릅니다.
   - 위도: `37.5772`
   - 경도: `126.9889`
7. 수 초 안에 이야기 발견 알림과 `[심사용] 합류시 사각지대 확인` 화면이 나타납니다.
8. `이야기 듣기`를 누르고 재생, 일시정지, 재생 위치 이동과 다시 듣기를 확인합니다.

이미 같은 오디오를 듣거나 건너뛴 상태에서 알림이 다시 나타나지 않으면 Roadio를 완전히 종료한 뒤 재실행합니다.

ADB로 위치를 적용할 경우에는 좌표 순서가 `경도 위도`임에 주의합니다.

```sh
adb emu geo fix 126.9950 37.5820
adb emu geo fix 126.9889 37.5772
```

## 프로젝트 구성

```text
app/        Pleos Connect 차량 앱
web/        iPhone 모바일 작성 웹앱
server/     위치 기반 검색·권한·음원 서버
docs/       API 계약 및 보조 문서
apk/        서명된 제출용 Release APK
```

`mobile/`은 초기 Android 모바일 탐색 모듈이며, 최종 시연의 모바일 작성 화면은 `web/`의 PWA입니다.

## 로컬 빌드

### 차량 앱

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Android Studio에서 실행할 때는 차량 모듈인 `app`을 선택합니다. `mobile`을 선택하면 최종 차량 화면이 아닌 초기 모바일 탐색 화면이 실행됩니다.

촬영·제출용 토큰은 Git에서 제외된 `local.properties`의 `roadio.filming.vehicleToken` 값으로 빌드 시 주입합니다. 토큰 원문은 코드와 문서에 기록하지 않습니다.

### 로컬 서버

```sh
python3 server/app.py --data-dir server/data create-user '작성자'
python3 server/app.py --data-dir server/data serve
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s server -p 'test_*.py' -v
```

서버는 기본적으로 `127.0.0.1:8787`에서 실행됩니다. API 요청·응답 형식은 [`docs/contracts/roadio-api-v1.yaml`](docs/contracts/roadio-api-v1.yaml)에 정의했습니다.

## 구현 범위와 한계

- 모바일 녹음 → Railway 저장 → Pleos AVD 가상 GPS 이동 → 차량 발견 → 선택 재생의 전체 흐름을 검증했습니다.
- FusedLocation `1.1.3`의 위치 업데이트를 Pleos Connect AVD에서 확인했습니다.
- 가상 GPS 시연은 실차 GPS 주행 검증과 구분합니다.
- 차속 기반 발견 거리 조정, 진행 방향·방향지시등 조건, 차량 경로 공유, 내비게이션 분할 화면은 향후 발전 범위입니다.
- Roadio는 안전을 보장하거나 운전 판단을 대신하는 시스템이 아닙니다. 실제 도로 표지, 공식 안내와 운전자의 직접 확인을 우선합니다.

## 개발·검증 문서

- [`Roadio_DESIGN-시안-2026-11-07.md`](Roadio_DESIGN-%EC%8B%9C%EC%95%88-2026-11-07.md)
- [`Roadio_WIREFRAME-2026-11-07.md`](Roadio_WIREFRAME-2026-11-07.md)
- [`Roadio_기획서_기능정의서_UX기준안.md`](Roadio_%EA%B8%B0%ED%9A%8D%EC%84%9C_%EA%B8%B0%EB%8A%A5%EC%A0%95%EC%9D%98%EC%84%9C_UX%EA%B8%B0%EC%A4%80%EC%95%88.md)
- `DEV-01-validation.md` ~ `DEV-11-validation.md`, `DEV-13-validation.md`, `DEV-14-validation.md`

개별 검증 문서에는 SDK·서버·모바일·차량 기능의 단계별 근거와 검증 한계를 기록했습니다.
