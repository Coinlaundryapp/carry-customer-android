# Native Bridge Changes — Frontend Handoff

이 문서는 CarryWebViewShell 네이티브 업그레이드에 따른 JavaScript Bridge API 변경 사항을 프론트엔드 엔지니어에게 전달하기 위해 작성되었습니다.

---

## 1. 새로 추가된 동기 메서드

### `getNetworkStatus(): string`

현재 네트워크 상태를 JSON 문자열로 반환합니다.

```javascript
const status = JSON.parse(AndroidBridge.getNetworkStatus());
// { isConnected: true, type: "wifi", isMetered: false }
```

**응답 형식:**
| 필드 | 타입 | 설명 |
|---|---|---|
| `isConnected` | boolean | 인터넷 연결 여부 |
| `type` | `"wifi"` \| `"cellular"` \| `"none"` | 연결 유형 |
| `isMetered` | boolean | 종량제 네트워크 여부 |

---

### `saveSecureToken(key: string, value: string): void`

EncryptedSharedPreferences를 사용하여 암호화된 토큰을 저장합니다.

```javascript
AndroidBridge.saveSecureToken("authToken", "eyJhbGciOi...");
```

---

### `getSecureToken(key: string): string`

저장된 암호화 토큰을 읽습니다. 없으면 빈 문자열을 반환합니다.

```javascript
const token = AndroidBridge.getSecureToken("authToken");
if (token) {
  // use token
}
```

---

### `removeSecureToken(key: string): void`

저장된 암호화 토큰을 삭제합니다.

```javascript
AndroidBridge.removeSecureToken("authToken");
```

---

## 2. 새로 추가된 비동기 메서드

### `checkAppUpdate(): string`

Google Play In-App Update 가능 여부를 확인합니다. `requestId`를 반환합니다.

```javascript
const requestId = AndroidBridge.checkAppUpdate();
// 콜백: { requestId, success: true, data: { available: true, storeVersion: 5 } }
```

**응답 데이터:**
| 필드 | 타입 | 설명 |
|---|---|---|
| `available` | boolean | 업데이트 가능 여부 |
| `storeVersion` | number | 스토어 최신 버전 코드 (없으면 0) |

---

## 3. 변경된 기존 메서드

### `requestCamera()` / `requestCamera(maxWidth, maxHeight, quality)`

**하위 호환**: 기존 `requestCamera()`는 그대로 동작합니다 (기본 압축 적용: 1024x1024, quality 80).

**새 오버로드**: 이미지 압축 옵션을 직접 지정할 수 있습니다.

```javascript
// 기존 방식 (하위 호환)
const id1 = AndroidBridge.requestCamera();

// 새 방식 (압축 옵션 지정)
const id2 = AndroidBridge.requestCamera(800, 800, 70);
```

**응답 형식 변경 (Breaking Change):**

이전:
```json
{ "requestId": "...", "success": true, "data": { "uri": "content://..." } }
```

이후:
```json
{
  "requestId": "...",
  "success": true,
  "data": {
    "uri": "content://...",
    "originalUri": "content://...",
    "width": 1024,
    "height": 768,
    "fileSize": 245760
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `uri` | string | 압축된 이미지 URI |
| `originalUri` | string | 원본 이미지 URI |
| `width` | number | 압축 후 너비 (px) |
| `height` | number | 압축 후 높이 (px) |
| `fileSize` | number | 압축 후 파일 크기 (bytes) |

> **참고**: 압축 실패 시 기존과 동일하게 `{ "uri": "..." }` 형식으로 원본 URI를 반환합니다.

---

### `openGallery()` / `openGallery(maxWidth, maxHeight, quality)`

`requestCamera`와 동일한 변경 사항이 적용됩니다.

```javascript
// 기존 방식 (하위 호환)
const id1 = AndroidBridge.openGallery();

// 새 방식 (압축 옵션 지정)
const id2 = AndroidBridge.openGallery(800, 800, 70);
```

응답 형식은 `requestCamera`와 동일합니다.

---

### `requestLogin()`

**Breaking Change**: 카카오 로그인 응답이 auth code에서 access token으로 변경되었습니다.

이전:
```json
{ "requestId": "...", "success": true, "data": { "code": "auth_code_here" } }
```

이후:
```json
{ "requestId": "...", "success": true, "data": { "accessToken": "access_token_here" } }
```

또한 `window.onLoginComplete(token)` 콜백도 auth code 대신 access token을 전달합니다.

**프론트엔드 대응 필요:**
- `data.code` → `data.accessToken`으로 참조 변경
- 서버에 auth code를 보내 token을 교환하는 로직이 있다면, 이제 직접 access token을 받으므로 교환 단계 제거

---

## 4. 변경된 이벤트

### `connectivityChanged`

이벤트 데이터가 확장되었습니다.

이전:
```json
{ "isConnected": true }
```

이후:
```json
{ "isConnected": true, "type": "wifi", "isMetered": false }
```

**하위 호환**: `isConnected` 필드는 그대로 존재하므로, 기존에 `isConnected`만 사용하는 코드는 수정 없이 동작합니다.

---

### `appUpdateAvailable` (새 이벤트)

앱 시작 시 Google Play 업데이트가 가능하면 자동으로 발송됩니다.

```javascript
window.CarryBridge.__onNativeEvent("appUpdateAvailable", {
  available: true,
  storeVersion: 5
});
```

---

## 5. Breaking Changes 요약

| 항목 | 변경 내용 | 프론트엔드 조치 |
|---|---|---|
| `requestCamera` 응답 | `{uri}` → `{uri, originalUri, width, height, fileSize}` | `data.uri`만 사용 시 변경 불필요. 새 필드 활용 가능 |
| `openGallery` 응답 | 동일 | 동일 |
| `requestLogin` 응답 | `{code}` → `{accessToken}` | `data.code` → `data.accessToken`으로 변경 필수 |
| `onLoginComplete` 콜백 | auth code → access token | token 처리 로직 변경 필수 |
| `connectivityChanged` 이벤트 | `{isConnected}` → `{isConnected, type, isMetered}` | 하위 호환. 새 필드 선택적 활용 |

---

## 6. 전체 Bridge API 목록

### 동기 메서드 (즉시 반환)

| 메서드 | 반환 | 상태 |
|---|---|---|
| `getDeviceInfo()` | string (JSON) | 기존 |
| `getAppVersion()` | string | 기존 |
| `getPushToken()` | string | 기존 |
| `getFCMToken()` | string | 기존 (별칭) |
| `showToast(message)` | void | 기존 |
| `hapticFeedback(type)` | void | 기존 |
| `shareText(title, text)` | void | 기존 |
| `shareUrl(title, url)` | void | 기존 |
| `copyToClipboard(text)` | void | 기존 |
| `readClipboard()` | string | 기존 |
| **`getNetworkStatus()`** | string (JSON) | **신규** |
| **`saveSecureToken(key, value)`** | void | **신규** |
| **`getSecureToken(key)`** | string | **신규** |
| **`removeSecureToken(key)`** | void | **신규** |

### 비동기 메서드 (requestId 반환, 콜백으로 결과 전달)

| 메서드 | 상태 |
|---|---|
| `requestBiometric(title, desc)` | 기존 |
| `requestLocation()` | 기존 |
| `requestCamera()` | **변경** (응답 확장) |
| `requestCamera(maxWidth, maxHeight, quality)` | **신규** |
| `openGallery()` | **변경** (응답 확장) |
| `openGallery(maxWidth, maxHeight, quality)` | **신규** |
| `requestLogin()` | **변경** (Kakao SDK, accessToken) |
| `requestNotificationPermission()` | 기존 |
| `openExternalBrowser(url)` | 기존 |
| **`checkAppUpdate()`** | **신규** |
| `closeApp()` | 기존 |

### 네이티브 → JS 이벤트

| 이벤트 | 상태 |
|---|---|
| `connectivityChanged` | **변경** (데이터 확장) |
| `deepLink` | 기존 |
| **`appUpdateAvailable`** | **신규** |

### 네이티브 → JS 콜백 함수

| 함수 | 상태 |
|---|---|
| `window.CarryBridge.__onNativeCallback(result)` | 기존 |
| `window.CarryBridge.__onNativeEvent(name, data)` | 기존 |
| `window.onLoginComplete(token)` | **변경** (accessToken) |
| `window.onNativeBackPressed()` | 기존 |
| `window.onPushNotification(data)` | 기존 |
| `window.onAppResume()` | 기존 |
