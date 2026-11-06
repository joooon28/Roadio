# DEV-02 Pleos 위치·오디오 기술 검증 기록

시작일: 2026-11-04 · 기술 검증 종료: 2026-11-06 · 상태: 완료 (AVD 기술 검증 범위) · 대상: Roadio 차량 앱

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
- [x] Media3 `MediaLibraryService` + 플랫폼 `MediaBrowserService` 호환 경로 선정·AVD 연결 검증
- [x] 음악·오디오 하위 분류 조사 및 심사 위험 판정: 기존 네 유형에 정확히 맞지 않음, 실제 심사 적합성은 Pleos 확인 필요
- [x] `CarUxRestrictions` 연동·정차 상태 확인 및 주행 전환 시험 가능 여부 확인 (이 AVD에서는 주행 전환 불가)
- [x] Roadio 개발 프로젝트의 SDK 접근 상태 확인 (Playground 표시·신청 내역 기준)
- [x] 실제 연동·가상 위치·샘플 오디오의 시연 표시 범위와 배포 전 확인 항목 결정

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

10. 사용자가 제공한 Roadio Pleos Playground 프로젝트 화면에서 SDK 메뉴의 FusedLocation은 `Coming Soon`/`준비 중`으로 표시됐다. `API 권한 관리`에는 Fleet·Vehicle Data만 표시됐으며, 사용자가 확인한 `API 신청 내역`에는 신청 기록이 없었다. 따라서 이 프로젝트에서 FusedLocation의 신청·승인 또는 배포 사용 권한은 확인되지 않았다. AVD에서 동작한 SDK 1.1.3의 기술 검증과 프로젝트 배포 권한은 서로 다른 근거로 관리한다.

11. Pleos 음악·오디오 가이드의 표준 미디어 연동 조건과 Android Media3 호환 가이드를 비교했다. Roadio에는 Media3 `MediaLibraryService`/`MediaLibrarySession`/`ExoPlayer`를 선택하고, 서비스에 플랫폼 `android.media.browse.MediaBrowserService` 액션도 함께 등록했다. `android:appCategory="audio"`, 런처 메타데이터, 미디어 재생 포그라운드 서비스 권한을 선언했다. Roadio의 짧은 위치 음성은 Pleos가 열거한 음악·팟캐스트·오디오북·라디오 중 어느 하위 분류와 맞는지 아직 불명확하다. 편의상 팟캐스트로 단정하지 않는다.
12. `:app:clean :app:assembleDebug` 성공 후 Pleos Connect v2.1.0 AVD에 설치했다. Android 플랫폼 `MediaBrowser` 연결 결과 `root=roadio-root`, 샘플 항목 1개(`dev02-sample`)를 받았다. 사용자 버튼에서 `playFromMediaId`를 호출한 뒤 `dumpsys media_session`에 Roadio 세션 `PLAYING(3)`이 표시됐고, `stop` 명령 뒤 `NONE(0)`으로 바뀌었다. 재생 직후 일시정지 명령을 보내 `PAUSED(2)` 상태도 확인했다. 차량 물리 버튼·Media Center 라이브러리 노출과 실제 차량 오디오 출력은 아직 검증하지 않았다.

## 2026-11-06 작업 과정·결과

13. Pleos 공식 주행 규제 개발 가이드에 따라 `android.car` 라이브러리의 `CarUxRestrictionsManager`를 진단 화면에 연결했다. 초기 상태를 읽고 변경 리스너를 등록하며, `isRequiresDistractionOptimization`이 참이면 진단 조작 버튼을 숨기도록 했다. 차량 속도·기어 값은 앱에서 읽지 않는다. `:app:clean :app:assembleDebug --no-daemon` 빌드 성공 후 Pleos Connect v2.1.0 AVD에서 `requiresDO=false, active=0`과 리스너 등록을 확인했다.
14. 공식 Pleos ADB 가이드의 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on`을 실행했으나 이 AVD는 `Can't find service: hmg_car_service`를 반환했다. `car_service`는 존재하지만 `cmd car_service help`가 사용자 빌드에서 `SecurityException`을 반환했다. 따라서 이 AVD에서 주행 제한 활성화 콜백과 화면 차단을 실측하지 못했다. 실제 주행 상태에서 화면 조작이 허용된다는 근거로 사용하지 않는다.
15. Pleos 음악·오디오 앱 가이드는 음악·팟캐스트·오디오북·라디오 하위 유형을 제시한다. Roadio의 위치에 남기는 짧은 개인 음성은 이 중 하나와 정확히 일치하지 않는다. 개발 중에는 음악·오디오 분류를 유지하되 하위 유형을 임의로 `팟캐스트`로 제출하지 않는다. 앱 마켓 심사·미디어 앱 라이브러리 노출 전에 Pleos에 적합한 분류와 표현을 확인해야 한다.
16. 시연 범위를 확정했다. AVD의 FusedLocation `1.1.3` 위치 콜백과 반경 이벤트는 **Pleos SDK를 통한 가상 위치 연동**으로 표기한다. `dev02_sample_voice.wav`는 **사전 준비한 합성 샘플**로 표기한다. 모바일 녹음→서버 저장→차량 주변 검색·재생은 아직 미구현으로 표기한다. Playground의 FusedLocation `준비 중` 및 신청 내역 없음은 **배포 권한 미확인**으로 표기한다. 영상·설명에서 이 네 범위를 섞지 않는다.

## 고려한 대안·선택 이유

- **위치:** FusedLocation을 우선 검증하되 의존성·권한·런타임이 확인되기 전까지 가상 위치 공급자를 분리한다. 캡슐 반경 검색은 서버 기능으로 설계한다.
- **음성:** 사전 녹음 샘플을 사용자가 선택해 재생하는 경로부터 시험한다. 모바일 음성 녹음과 차량 재생은 별도 기능이다.
- **차량 UI:** 주행 중 목록 탐색 가능 여부를 추정하지 않고 Pleos `CarUxRestrictions`와 카테고리 조건을 확인한다.

## 완료 판정·배포 전 확인 항목

**DEV-02의 기술 검증 목표는 완료했다.** Roadio AVD에서 확인한 위치·반경·미디어 브라우징·선택 재생 범위, 정책상 요구되는 통합 방식, 시연 대체와 확인 불가 범위를 결정·기록했다. 이는 기능 완성이나 Pleos App Market 심사 통과를 뜻하지 않는다.

- **SDK 권한:** Roadio Playground에서 FusedLocation은 `준비 중`, API 신청 내역은 없음. 실제 차량·배포 사용 허가를 Pleos에 재확인한다. SDK가 허용되지 않으면 제출 시 가상 위치 시연임을 유지한다.
- **카테고리:** 짧은 위치 음성 캡슐은 Pleos가 제시한 음악·팟캐스트·오디오북·라디오 중 명확한 하위 분류가 없다. 제출 전 Pleos의 하위 분류·미디어 앱 라이브러리 심사 기준 확인이 필요하다.
- **주행 UX:** AVD 정차 상태만 실측했다. 이 이미지에서는 주행 규제 강제 전환 명령을 사용할 수 없어 ON 상태의 콜백과 화면 동작은 미검증이다. 현재 앱은 DO를 선언하지 않았으므로 공식 지역 정책상 주행 중 화면 사용을 가정하지 않는다. DEV-04 화면 구현과 제출 전 지원 이미지/실차에서 다시 검증한다.
- **오디오:** AVD에서 Media3 세션 상태와 Android 오디오 포커스는 확인했지만, 실제 차량 물리 버튼·Media Center 라이브러리 노출·스피커 청취·실제 사용자 음성 품질은 미검증이다. 사용자 청취 확인과 통합 시연은 후속 단계에 둔다.
- **기능 범위:** 서버 캡슐 검색·인증·공개 범위·모바일 녹음은 DEV-03 이후 구현한다. 현재 진단 화면은 샘플을 읽는 기술 시험용이다.

## 공식 근거

- [FusedLocation 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [FusedLocation 가져오기](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
- [FusedLocation 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)
- [Pleos 음악·오디오 앱 개발 가이드](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide)
- [주행 제한 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)
- [Pleos 주행 규제 개발 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [Pleos ADB 주행 규제 명령 가이드](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/adb-command-reference-guide)

- [Pleos Maven FusedLocation 메타데이터](https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/FusedLocation/maven-metadata.xml)

- [Android Media3 `MediaLibraryService` 가이드](https://developer.android.com/media/media3/session/serve-content)
- [Android Media3 릴리스](https://developer.android.com/jetpack/androidx/releases/media3)
