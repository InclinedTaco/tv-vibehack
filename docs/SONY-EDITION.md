# Sony edition: implementation and validation

This fork contains the configuration tested on a Sony KD-43X7500E running Android 9/API 28.
The launcher is adapted from Gathin's tv-vibehack; the Market Watcher companion is in `market-watcher/`.

## Source map

| Feature | File under `launcher/app/src/main/` |
| --- | --- |
| App order, named HDMI routes | `res/values/config.xml` |
| Home arrangement | `res/layout/activity_main.xml`, `java/com/gathin/tvhome/MainActivity.java` |
| All Apps | `java/com/gathin/tvhome/AllAppsActivity.java` |
| Market, receiver and camera artwork | `java/com/gathin/tvhome/DeviceBannerDrawable.java` |
| Weather coordinates and cache | `java/com/gathin/tvhome/WeatherWidget.java` |
| Weather icons | `java/com/gathin/tvhome/WeatherIconDrawable.java` |
| Navigation sounds | `java/com/gathin/tvhome/FocusSounds.java` |
| Photographic screensaver | `java/com/gathin/tvhome/SceneryDream.java` |
| Photo sources and credits | `assets/scenery/CREDITS.txt` |

HDMI 2 maps to Sony's `HW3` input and HDMI 3 to `HW4`. These IDs are device-specific.
The input menu lists configured shortcuts, not a live connection detector. Edit the four
parallel `input_shortcut_*` arrays for another installation. Weather currently uses Erode's
city coordinates; it does not use GPS. App packages and weather location are source configuration,
not an in-app preferences screen.

## Screensaver and sound

Fifty real 3840x2160 photographs are bundled for offline use. `assets/scenery/scenes.json`
provides the catalog, captions and attribution. Scenes rotate every
60 seconds with a 2.6-second dissolve and slow pan/zoom. The clock is 48sp, date 14sp, and
AM/PM 12sp. The date has a 6dp inset. The clock also shifts slightly with scene changes.

The tested TV renders Android's UI at 1920x1080. Photos are decoded on a background thread
and resized to about 115% of display width; 4K source assets do not imply native 4K UI output.
The screensaver makes no photo network requests and plays no audio.

The launcher preloads the TV's existing directional Sony/Android navigation sounds with
SoundPool. Valid focus moves in all four directions play at gain 1.0; only one stream is
allowed at a time. It respects the system sound-effects preference and falls back to Android's
focus sound when files cannot be loaded. No master or media volume is changed. The briefly
tested synthesized click was reverted; it is not included in this version.

## Market Watcher

Native Java, no WebView and no additional third-party libraries. Source is under
`market-watcher/app/src/main/java/local/tv/marketwatcher/`.

- Chennai 22K/24K gold rates from LiveChennai, including monthly history archives.
- Chennai silver rates from Goodreturns, including up to a year of daily history. Source
  kilogram prices are validated and converted to per-gram prices; dates are checked and shown.
- India: Nifty 50, Sensex, Reliance, TCS, HDFC Bank, ICICI Bank, Infosys and Bharti Airtel.
- US: S&P 500, Nasdaq, Apple, Microsoft, NVIDIA, Amazon, Alphabet and Tesla.
- 1D, 1W, 1M and 1Y controls for all instruments; currencies and index points are separate.
- OK on a card enters the period controls; Back or Down returns to the selected card.
- Metal 1D shows one dated daily observation; stock 1W covers five trading sessions.
- Active watchlist refreshes about once a minute; metals about every 15 minutes.
- Cached/error states and timestamps distinguish available data from fresh results.

Stock charts use Yahoo Finance's chart endpoint; gold uses LiveChennai's webpages.
Silver uses Goodreturns' public page and chart endpoint, obtaining the temporary public
chart token at runtime. No token or account credential is bundled or logged.
The last successful responses are cached in private preferences. Completed gold archive
months are reused; the current month is refreshed. No market prices are bundled in the APK.
These are external, unofficial integrations that can change or rate-limit. Values may be
delayed and metals are daily published rates, not tick-by-tick exchange feeds.

## Verification

- Launcher `assembleRelease` and its release vital-lint checks passed.
- Final launcher APK was installed on the Sony; enlarged screensaver clock/date and restored
  navigation sound were accepted by the user.
- All 50 assets checked at 3840x2160 and visually reviewed in contact sheets; the installed
  screensaver reports a 50-scene catalog. Automatic transitions and remote-button exit checked.
- Navigation sound playback IDs confirmed for all four directions.
- Market Watcher `assembleDebug` and `lintDebug` passed during implementation; installed app
  was visually checked for full-screen display and unclipped focused cards/1Y control.
- Focused metal parser checks passed for kilogram/gram conversion, invalid dates and units,
  gold archive validation and calendar range boundaries. Real source snapshots parsed 365
  silver observations and a 30-day gold archive. See `market-watcher/tests/MetalHistoryChecks.java`.
- Full launcher `lintRelease` still reports eight existing errors: one fine/coarse location
  permission issue and seven missing default-string translations. Vital lint passing does
  not mean the full lint suite is clean.

A sample on-device reading was approximately 41 MiB launcher PSS, with 1.90 GiB total RAM
and 356 MiB available system-wide. These are a momentary snapshot, not fixed requirements
or a performance guarantee. App/media activity changes available RAM.

## Bluetooth and AirPlay

Bluetooth helper source and APK are published separately:
https://github.com/InclinedTaco/old-android-tv-bluetooth-earphones

Sony lists HID/HOGP/SPP, but not A2DP, for this model. In the tested firmware an A2DP service
was present and functional. The helper performs discovery, normal system pairing and an
explicit connection request to that service. This suggests a limitation in the stock
connection flow; it does not establish deliberate blocking or universal headphone support.
See https://www.sony.co.in/electronics/support/televisions-projectors-lcd-tvs-android-/kd-43x7500e/specifications

AirPlay was configured using the separate open-source receiver
https://github.com/jqssun/android-airplay-server (v0.0.33). Its source/APK is not bundled here.
The receiver service and discovery were checked; actual iPhone playback was not confirmed.

## Credits and licenses

Original launcher: Gathin23/tv-vibehack, MIT; original copyright and history retained.
Outfit font: SIL Open Font License, included in `launcher/FONT-LICENSE-OFL.txt`.
Three Unsplash photos: Frantisek Duris, Bernd Dittrich and Fabian Struwe. Another 47 photos
come from Wikimedia Commons under individual CC BY or CC BY-SA licenses. All source URLs,
authors, license links and changes are in `launcher/app/src/main/assets/scenery/CREDITS.txt`
and `scenes.json`. Modified photographs retain their respective licenses; the code's MIT
license does not cover them. Third-party app names and marks belong to their owners.
