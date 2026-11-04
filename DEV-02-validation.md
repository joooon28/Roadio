# DEV-02 Pleos 위치·오디오 기술 검증 기록

시작일: 2026-11-04 · 상태: 진행 중 · 대상: Roadio 차량 앱

## 작업 목표·근거

Roadio의 P0 차량 경험은 현재 위치로 주변 캡슐을 찾고, 사용자가 선택한 음성을 차내에서 재생하는 것이다. 개발 전에 SDK 의존성·권한·AVD 지원, 차량 미디어 연동과 주행 중 UX 조건을 확인한다. 기존 DriveFit 검증은 참고 근거이며 Roadio 구현 성공으로 승계하지 않는다.

## 할 일

- [x] 위치·재생에 필요한 기술 후보와 시연 대체 범위 정리
- [x] FusedLocation 공식 의존성·권한 안내 및 Maven 파일 응답 확인
- [x] Pleos 음악·오디오 앱과 주행 제한 가이드 검토
- [x] Roadio 프로젝트에서 FusedLocation 의존성 빌드 확인
- [x] Pleos AVD에서 `isSupported()`·초기화·위치 콜백 확인
- [x] 가상 차량 위치를 바꿔 업데이트·반경 동작 확인
- [x] 샘플 음성의 사용자 선택 재생·일시정지·종료 및 오디오 포커스 확인
- [ ] MediaBrowserService/MediaSession 또는 Media3 경로와 카테고리 심사 조건 확정
- [ ] `CarUxRestrictions` 적용 범위와 정차·주행 시 화면 동작 확인
- [ ] Roadio 개발 프로젝트의 SDK 접근 상태 확인
- [ ] 실제 연동·가상 위치·샘플 오디오의 시연 표시 범위 결정

**완료 기준:** 위치와 오디오의 실제 동작 가능 범위를 앱 실행 결과로 설명하고, 미지원·권한·정책 제약 및 시연 대체 방식을 근거와 함께 기록한다.

## 2026-11-04 작업 과정·결과

1. Roadio 기획서의 F-05~07을 기준으로 차량 위치 조회, 주변 목록, 선택 재생을 검증 대상으로 골랐다. 모바일 녹음은 Android 표준 API로 구현할 후속 범위다.
2. Pleos 공식 FusedLocation 문서는 `ai.pleos.playground:FusedLocation:1.1.2`와 `pleos.car.permission.FUSED_LOCATION`을 안내한다. 위치 조회 권한은 `dangerous`로 표시돼 있다. `isSupported()`로 환경 지원을 먼저 확인하라고 안내한다.
3. 공식 Maven POM 주소에 HTTP 요청한 결과 **404**였다. 명령: `curl -L -sS -o /dev/null -w '%{http_code}\n' https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/1.1.2/FusedLocation-1.1.2.pom`. 앞선 DriveFit 빌드에서도 동일 좌표의 의존성을 찾지 못했다. 이 결과는 현재 URL에서 파일을 못 받았다는 뜻이며 SDK 전체가 미제공이라는 결론은 아니다.
4. Pleos 음악·오디오 앱 가이드는 음악·오디오 카테고리의 표준 차량 미디어 연동에 `MediaBrowserService`와 `MediaSession`을 요구한다고 적고, 같은 문서 후반에는 Media3 `MediaSession`/`MediaLibraryService`를 현재 표준으로 권장한다. Roadio의 분류와 채택 경로는 확인이 필요하다. 자동 재생 금지와 오디오 포커스 처리 조건도 안내한다.
5. 주행 제한 정책은 지역·차종·플랫폼 버전에 따라 달라질 수 있다. Roadio는 차량 상태 API 접근을 가정하지 않고 정차 시연을 우선한다.

6. 공식 Pleos Maven 메타데이터에는 FusedLocation `1.0.4`, `1.1.3`이 게시되어 있고 `1.1.3` POM은 HTTP 200이었다. 공식 가이드의 `1.1.2` 예시는 현재 저장소와 어긋난다. Roadio에 `1.1.3` 의존성과 `pleos.car.permission.FUSED_LOCATION`을 넣어 `:app:clean :app:assembleDebug` 빌드에 성공했다.
7. Pleos Connect v2.1.0 AVD에서 Roadio 진단 화면을 설치·실행했다. 권한 허용 뒤 `isSupported=true`, `initialize` 완료, 위치 콜백 등록 및 좌표 수신을 Logcat으로 확인했다. `adb emu geo fix 127.0276 37.4979`로 위치를 바꾸자 콜백이 새 좌표를 받았다.
8. 강남역 좌표 기준 500m `ProximityAlertRequest` 등록 후, 가상 위치를 반경 밖(`127.0500, 37.5100`)으로 이동하자 `EXIT`, 다시 안(`127.0276, 37.4979`)으로 이동하자 `ENTER` 이벤트가 기록됐다. 이는 AVD의 가상 위치를 Pleos SDK가 처리한 결과이며 실제 차량 위치 검증은 아니다.
9. 사전 준비한 합성 음성 WAV를 화면 버튼으로 선택해 재생했다. 오디오 포커스 요청 결과 `1`(허용), 재생 시작·완료·종료 및 포커스 해제 로그를 확인했다. 버튼으로 일시정지와 재시작도 확인했다. 실제 스피커에서 들리는지는 확인하지 못했다. 자동 재생은 구현하지 않았다.

## 고려한 대안·선택 이유

- **위치:** FusedLocation을 우선 검증하되 의존성·권한·런타임이 확인되기 전까지 가상 위치 공급자를 분리한다. 캡슐 반경 검색은 서버 기능으로 설계한다.
- **음성:** 사전 녹음 샘플을 사용자가 선택해 재생하는 경로부터 시험한다. 모바일 음성 녹음과 차량 재생은 별도 기능이다.
- **차량 UI:** 주행 중 목록 탐색 가능 여부를 추정하지 않고 Pleos `CarUxRestrictions`와 카테고리 조건을 확인한다.

## 남은 한계·후속 작업

이번 검증은 SDK가 Pleos AVD의 가상 위치를 읽고 반경 이벤트를 전달하는 것과 Android `MediaPlayer`가 샘플을 재생했다는 범위다. 실제 차량, 실제 위치, 음질·출력 장치, 서버 캡슐 검색은 확인하지 못했다. 진단 화면은 개발용이며 사용자 캡슐 검색 기능이 아니다.

음악·오디오 앱의 `MediaBrowserService`/`MediaSession` 또는 Media3 적용과 카테고리 심사 조건, `CarUxRestrictions`에 따른 정차·주행 화면, Roadio 개발 프로젝트의 SDK 사용 권한을 확인해야 한다. 이 항목이 남아 있어 DEV-02는 **진행 중**이다. 시연에서는 가상 위치와 합성 샘플을 표시하고 실제 연동으로 오인되지 않게 한다.

## 공식 근거

- [FusedLocation 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [FusedLocation 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
- [FusedLocation 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)
- [Pleos 음악·오디오 앱 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide)
- [주행 제한 정책](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)

- [Pleos Maven FusedLocation 메타데이터](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/maven-metadata.xml)
