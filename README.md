# tv-vibehack — Sony edition

A minimal Android TV home screen with a large clock, weather, named HDMI tiles, an offline scenery screensaver, and a native Market Watcher app.

Customized from [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack), with the original history and MIT attribution preserved. Tested on **Sony KD-43X7500E, Android 9**. This is the configuration used on that TV; other models need their own input IDs and app packages.

## What is included

- Two rows of large app tiles: YouTube, Prime Video, Netflix, JioHotstar, ZEE5, Market Watcher, Airtel Xstream and CCTV.
- Circular Inputs, All Apps and Settings controls; no greeting or large background capsule behind the app rows.
- Large home clock, Erode weather icon and temperature, and date below.
- Named shortcuts for HDMI 2 (Airtel Xstream) and HDMI 3 (CCTV).
- Preloaded original TV navigation sounds for horizontal and vertical focus moves.
- Offline scenery screensaver: 50 real 3840×2160 photographs, gentle motion, crossfades, and a clock/date overlay.
- Market Watcher: Chennai gold and silver rates, Indian and US stocks/indices, and 1D / 1W / 1M / 1Y charts for every instrument.

The input list is configured for this installation; it does not automatically detect every connected device. The 4K photographs are scaled for the TV's actual Android UI resolution (1080p on the tested Sony).

## Download

| App | APK | Package |
| --- | --- | --- |
| Launcher + screensaver | [tvhome-sony.apk](https://github.com/InclinedTaco/tv-vibehack/releases/download/sony-v1.0/tvhome-sony.apk) | `com.gathin.tvhome` |
| Market Watcher | [market-watcher.apk](https://github.com/InclinedTaco/tv-vibehack/releases/download/sony-v1.0/market-watcher.apk) | `local.tv.marketwatcher` |

The launcher download is about 105 MiB because all 50 photos are bundled. APKs are distributed through [GitHub Releases](https://github.com/InclinedTaco/tv-vibehack/releases/tag/sony-v1.0), separately from the source checkout.

[SHA-256 checksums](dist/SHA256SUMS). These are sideload builds signed with the development key used on the tested TV. Signing keys are not included. A locally rebuilt APK will use your own key and may not install over these binaries without removing the existing app first.

Bluetooth headphones support lives in the separate [old Android TV Bluetooth helper](https://github.com/InclinedTaco/old-android-tv-bluetooth-earphones). AirPlay uses a separate third-party receiver; see [implementation notes](docs/SONY-EDITION.md#bluetooth-and-airplay).

## Install with ADB

Download both APKs into a local `dist` folder. Enable debugging in the TV's developer settings, connect from a trusted computer on the same network, and approve the TV's authorization prompt. Replace `TV_IP` with your TV's address:

```sh
adb connect TV_IP:5555
adb -s TV_IP:5555 install -r dist/market-watcher.apk
adb -s TV_IP:5555 install -r dist/tvhome-sony.apk
adb -s TV_IP:5555 shell am start -n com.gathin.tvhome/.MainActivity
adb -s TV_IP:5555 shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```

Try the new home screen before changing any stock packages. Installing these APKs does not remove apps or apply the earlier TV cleanup. The original project's debloating scripts remain in this fork but are not a universal removal list for Sony TVs.

Choose **Scenery** in the TV's screensaver settings, or record the current component and set it through ADB:

```sh
adb -s TV_IP:5555 shell settings get secure screensaver_components
adb -s TV_IP:5555 shell settings put secure screensaver_components com.gathin.tvhome/.SceneryDream
adb -s TV_IP:5555 shell settings put secure screensaver_enabled 1
```

Use the TV's settings to set the idle delay. The tested installation uses five minutes. Any remote key exits the screensaver. To restore the previous screensaver, select it in Settings or write back the component you recorded.

To return to the stock home, choose it in the TV's home-app settings. If it was disabled earlier, re-enable its actual package first (on the tested TV: `adb shell pm enable --user 0 com.google.android.tvlauncher`). Keep a working home app selected before uninstalling this launcher.

## Build

Requirements: JDK 17 or a compatible newer JDK, Android SDK platform/build-tools 34, and `ANDROID_HOME` pointing to your SDK. Gradle 8.9 is provided through the wrapper. The first build needs network access to download dependencies.

From the repository root on Windows:

```powershell
.\launcher\gradlew.bat -p launcher assembleRelease
.\launcher\gradlew.bat -p market-watcher assembleDebug
```

On macOS/Linux:

```sh
./launcher/gradlew -p launcher assembleRelease
./launcher/gradlew -p market-watcher assembleDebug
```

Outputs:

- `launcher/app/build/outputs/apk/release/app-release.apk`
- `market-watcher/app/build/outputs/apk/debug/app-debug.apk`

The launcher release build enables code/resource shrinking and uses debug signing for sideloading. Market Watcher has no third-party runtime dependencies.

## Customize

Edit `launcher/app/src/main/res/values/config.xml` for pinned apps, hidden apps and named input shortcuts. Edit `WeatherWidget.java` for the weather city, and `SceneryDream.java` for screensaver typography and timing. The included upstream theme presets predate this custom layout and may overwrite its configuration.

See [Sony edition implementation notes](docs/SONY-EDITION.md) for the source map, data refresh behavior, device-specific details, validation, known lint issues and credits. The original README is preserved at [README.upstream.md](README.upstream.md).

## Data and permissions

The launcher fetches weather from Open-Meteo; screensaver photos work entirely offline. Market Watcher fetches Yahoo Finance chart data, LiveChennai gold rates and Goodreturns Chennai silver rates. Stocks refresh about every minute and metals every 15 minutes. The last successful responses are cached locally for fallback; prices are not bundled or invented. The app shows source timestamps and saved-data/error states.

Press **OK** on a stock or metal card to enter the chart's period controls. Use Left/Right and OK to choose **1D, 1W, 1M or 1Y**; Back or Down returns to the card. All four controls remain fully visible when focused. Metal prices are published daily, so their 1D view shows one dated observation rather than an intraday graph. Stock 1W covers five trading sessions. Providers can delay, change or rate-limit their data.

No account or API key is bundled. Internet/network/Wi-Fi permissions and the upstream location permission are declared by the launcher; location was inherited for network-name access. Weather uses fixed city coordinates. Optional accessibility services from upstream handle remote keys and are not enabled by installing the APK alone.

## License and attribution

Code: [MIT](LICENSE), based on Gathin's original work. Outfit: [SIL OFL](launcher/FONT-LICENSE-OFL.txt). Photos: three from Unsplash and 47 from Wikimedia Commons under their individual Unsplash, CC BY or CC BY-SA licenses. [Photo credits](launcher/app/src/main/assets/scenery/CREDITS.txt) and [catalog](launcher/app/src/main/assets/scenery/scenes.json) include each author, original source, license link and crop/resize changes. The photographs are not covered by the code's MIT license. This project is not affiliated with Apple, Sony, or the streaming/data providers.
