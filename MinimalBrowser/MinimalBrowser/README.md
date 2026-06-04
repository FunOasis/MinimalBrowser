# Minimal Browser for Android

A lightweight Android browser with **built-in ad, tracker, and malware blocking** — the realistic WebView alternative to uBlock Origin.

---

## Features

| Feature | Details |
|---|---|
| 🛡 Ad blocking | Host-based blocklist (EasyList / Steven Black compatible) |
| 🔍 URL pattern filtering | Keyword-based request blocking (similar to uBlock filters) |
| 🎨 Cosmetic filtering | CSS element-hiding injected into every page |
| 📊 Live blocked counter | Badge in toolbar shows blocked requests per page |
| 🏠 Custom homepage | Set your preferred start page |
| ⚡ JS toggle | Disable JavaScript for faster/more private browsing |
| 🖥 Desktop mode | Switch user-agent to request desktop sites |
| 🔄 Pull-to-refresh | Swipe down to reload |
| 🔗 Intent handling | Opens links from other apps |

---

## Architecture

```
AdBlocker.kt              ← Core blocking engine (singleton)
  ├── blocklist.txt        ← Domain blocklist (assets/)
  ├── filters.txt          ← URL keyword patterns (assets/)
  └── cosmetic.txt         ← CSS element-hiding rules (assets/)

BlockingWebViewClient.kt  ← WebViewClient that intercepts every request
MainActivity.kt           ← Browser UI, address bar, nav controls
SettingsActivity.kt       ← Settings screen
Prefs.kt                  ← SharedPreferences wrapper
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 26+
- Kotlin 1.9+

### Build
```bash
git clone <repo>
cd MinimalBrowser
./gradlew assembleDebug
```

### Install
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Upgrading the Blocklists

The bundled lists in `app/src/main/assets/` are a starter set. For full coverage, replace `blocklist.txt` with a complete hosts file:

```bash
# Download Steven Black's combined hosts file (~100k domains)
curl -o app/src/main/assets/blocklist.txt \
  https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts

# Or the "fakenews + gambling + porn + social" variant:
curl -o app/src/main/assets/blocklist.txt \
  https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn-social/hosts
```

For `filters.txt`, compatible with uBlock/EasyList syntax (keyword-only lines):
```bash
curl https://easylist.to/easylist/easylist.txt | grep -v '^!' | grep -v '##' > app/src/main/assets/filters.txt
```

---

## How Blocking Works

### 1. Host Blocking (fastest)
Every outgoing request's host is checked against `blocklist.txt`.  
Subdomain walking: a rule for `example.com` also blocks `ads.example.com`.

### 2. URL Pattern Matching
The full URL is checked against each line of `filters.txt`.  
Example: any URL containing `/ads/` or `pixel.gif` is blocked.

### 3. Cosmetic Filtering (element hiding)
After each page loads, a JavaScript snippet injects CSS that sets `display: none` on elements matching selectors in `cosmetic.txt`.  
This removes ad containers, cookie banners, and newsletter popups that survive network-level blocking.

---

## Permissions

| Permission | Reason |
|---|---|
| `INTERNET` | Browse the web |
| `ACCESS_NETWORK_STATE` | Check connectivity |
| `DOWNLOAD_WITHOUT_NOTIFICATION` | Silent file downloads |

No location, camera, microphone, or contacts permissions are requested.

---

## Limitations vs. Real uBlock Origin

| Capability | This app | uBlock Origin |
|---|---|---|
| Network-level blocking | ✅ | ✅ |
| Element hiding (cosmetic) | ✅ | ✅ |
| Dynamic filtering | ❌ | ✅ |
| Scriptlets | ❌ | ✅ |
| Per-site rules | ❌ | ✅ |
| Extension ecosystem | ❌ | ✅ |
| Auto-updating lists | Manual | Auto |

For near-uBlock parity on Android, consider **Kiwi Browser** (Chromium-based, supports real Chrome extensions including uBlock Origin).

---

## License
MIT
