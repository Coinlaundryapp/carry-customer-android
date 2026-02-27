# CarryWebViewShell

Carry 서비스의 Android WebView Shell 앱. 웹 앱을 네이티브 WebView로 래핑하고, JavaScript Bridge를 통해 네이티브 기능(생체인증, 위치, 카메라, 푸시 등)을 웹에 노출한다.

## 아키텍처

```
MainActivity
├── WebView (CarryWebViewClient + CarryWebChromeClient)
├── NativeCallDispatcher       ← Native → JS 콜백/이벤트 전달
├── CarryBridge (@JavascriptInterface) ← JS → Native 호출 수신
│   └── onAsyncRequest → handleAsyncBridgeRequest() 라우팅
├── Handler 클래스들
│   ├── BiometricRequestHandler
│   ├── LocationRequestHandler
│   ├── MediaRequestHandler
│   └── LoginRequestHandler
├── PermissionHandler          ← 런타임 권한 요청 통합
├── SplashLoadingView / ErrorView
└── WebViewEventDispatcher     ← FCM 등 외부 컴포넌트 → WebView 이벤트 전달
```

## 프로젝트 구조

```
app/src/main/java/com/carrylabs/carry/webviewshell/
├── MainActivity.kt                    # 메인 Activity
├── CarryApplication.kt                # Application 클래스
├── bridge/
│   ├── CarryBridge.kt                 # @JavascriptInterface (JS → Native)
│   ├── NativeCallDispatcher.kt        # Native → JS 함수 호출
│   ├── BridgeResult.kt                # 비동기 콜백 결과 DTO
│   ├── BridgeCallbackManager.kt       # requestId 생성
│   ├── WebViewEventDispatcher.kt      # 이벤트 디스패처 인터페이스 + Registry
│   └── handlers/
│       ├── BiometricRequestHandler.kt # 생체인증
│       ├── LocationRequestHandler.kt  # GPS 위치 + Geolocation 권한
│       ├── MediaRequestHandler.kt     # 카메라 / 갤러리 / 파일 선택
│       └── LoginRequestHandler.kt     # 카카오 OAuth 로그인
├── webview/
│   ├── WebViewSetup.kt                # WebView 설정 (UA, 캐시, 혼합 콘텐츠 등)
│   ├── CarryWebViewClient.kt          # URL 스킴 처리 (intent://, 결제앱 등)
│   ├── CarryWebChromeClient.kt        # 파일 선택, 진행률, Geolocation
│   ├── UrlWhitelistManager.kt         # 허용 도메인 + 결제앱 스킴 관리
│   └── CookieHelper.kt               # 쿠키 설정
├── fcm/
│   ├── CarryFirebaseMessagingService.kt # FCM 수신 + 포그라운드 WebView 전달
│   └── PushTokenManager.kt           # FCM 토큰 저장/조회
├── permission/
│   └── PermissionHandler.kt          # 런타임 권한 요청 유틸리티
├── ui/
│   ├── SplashLoadingView.kt          # 스플래시 로딩 화면
│   └── ErrorView.kt                  # 네트워크 오류 화면
└── util/
    ├── DeviceInfo.kt                  # 디바이스 정보 수집
    ├── BiometricHelper.kt             # 생체인증 유틸리티
    ├── FileProviderHelper.kt          # 카메라 촬영 URI 생성
    └── NetworkUtils.kt                # 네트워크 상태 감지
```

## JavaScript Bridge API

WebView에서 `window.AndroidBridge` (또는 `window.CarryNative`)로 접근한다.

### 동기 메서드

| 메서드 | 반환 | 설명 |
|---|---|---|
| `getDeviceInfo()` | `string` (JSON) | 디바이스 정보 (`platform`, `model`, `osVersion` 등) |
| `getAppVersion()` | `string` | 앱 버전 (`versionName`) |
| `getPushToken()` | `string` | FCM 푸시 토큰 |
| `getFCMToken()` | `string` | FCM 푸시 토큰 (alias) |
| `showToast(message)` | `void` | 네이티브 Toast 표시 |
| `hapticFeedback(type)` | `void` | 진동 피드백 (`light`/`medium`/`heavy`) |
| `shareText(title, text)` | `void` | 시스템 공유 시트 |
| `shareUrl(title, url)` | `void` | URL 공유 (shareText alias) |
| `copyToClipboard(text)` | `void` | 클립보드 복사 |
| `readClipboard()` | `string` | 클립보드 읽기 |

### 비동기 메서드

모든 비동기 메서드는 `requestId`(string)를 반환하며, 결과는 `window.CarryBridge.__onNativeCallback(result)`로 전달된다.

| 메서드 | 설명 |
|---|---|
| `requestBiometric(title, description)` | 생체인증 요청 |
| `requestLocation()` | GPS 위치 요청 |
| `requestCamera()` | 카메라 촬영 |
| `openGallery()` | 갤러리 이미지 선택 |
| `requestLogin()` | 카카오 OAuth 로그인 |
| `requestNotificationPermission()` | 알림 권한 요청 |
| `openExternalBrowser(url)` | 외부 브라우저(Chrome Custom Tab)로 URL 열기 |
| `closeApp()` | 앱 종료 |

### 콜백 결과 형식

```json
{
  "requestId": "req_1234567890",
  "success": true,
  "data": { ... },
  "error": "에러 메시지 (실패 시)"
}
```

### Native → JS 이벤트

앱에서 웹으로 전달되는 이벤트:

| JS 함수 | 트리거 |
|---|---|
| `window.onLoginComplete(token)` | 카카오 로그인 완료 |
| `window.onNativeBackPressed()` | 네이티브 뒤로가기 버튼 (정의되어 있으면 호출) |
| `window.onPushNotification(data)` | 포그라운드 푸시 수신 |
| `window.onAppResume()` | 앱이 포그라운드로 복귀 |
| `window.CarryBridge.__onNativeEvent(name, data)` | 범용 이벤트 (`connectivityChanged`, `deepLink`, `appResumed`) |

## 빌드 환경

| 항목 | 값 |
|---|---|
| compileSdk / targetSdk | 35 |
| minSdk | 28 (Android 9) |
| Kotlin | JVM 11 |
| JDK | 21 (Android Studio JBR 권장) |

## Product Flavors

| Flavor | applicationId suffix | BASE_URL | 비고 |
|---|---|---|---|
| `dev` | `.dev` | `http://10.0.2.2:3000` | 로컬 개발 (cleartext 허용) |
| `staging` | `.staging` | `https://staging.carry.com` | 스테이징 |
| `prod` | (없음) | `https://app.carry.com` | 프로덕션 |

## 빌드 및 실행

```bash
# dev flavor 디버그 빌드
./gradlew assembleDevDebug

# staging flavor 디버그 빌드
./gradlew assembleStagingDebug

# prod flavor 릴리스 빌드
./gradlew assembleProdRelease

# 테스트 실행
./gradlew testDevDebugUnitTest
```

> JDK 21을 사용해야 합니다. 시스템 JDK가 25인 경우 Android Studio 번들 JBR을 `JAVA_HOME`으로 지정하세요.

## 딥링크

- **Custom Scheme**: `carry://` — `carry://path`가 `BASE_URL + /path`로 매핑
- **App Links**: `https://app.carry.com/*`
- **OAuth 콜백**: `carry://oauth/kakao?code=xxx` — 카카오 로그인 결과 처리

## URL 스킴 처리

WebView에서 발생하는 외부 URL 스킴을 처리한다:

- **`intent://`**: 앱 실행 시도 → `browser_fallback_url` → Play Store fallback
- **결제앱 스킴**: `kakaopay`, `ispmobile`, `supertoss`, `naverpay` 등 25+ 스킴 지원
- **허용 도메인**: `carry.com`, `kakao` 등 화이트리스트 기반 네비게이션

## 권한

| 권한 | 용도 |
|---|---|
| `INTERNET` | WebView 네트워크 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS 위치 / Geolocation |
| `CAMERA` | 카메라 촬영 |
| `POST_NOTIFICATIONS` | 푸시 알림 (Android 13+) |
| `USE_BIOMETRIC` | 생체인증 |
| `VIBRATE` | 진동 피드백 |
| `READ_MEDIA_IMAGES` | 갤러리 접근 |

## 설정

- **`KAKAO_CLIENT_ID`**: 각 flavor의 `build.gradle.kts`에 카카오 OAuth 클라이언트 ID 설정
- **`google-services.json`**: 각 flavor 디렉토리(`app/src/dev/`, `app/src/staging/`, `app/src/prod/`)에 Firebase 설정 파일 배치
