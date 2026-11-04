# گزارش بهینه‌سازی بسته‌بندی و کاهش حجم — نسخه ۱۷.۱ (`Version 17.1`)

## ۱. مشخصات بسته‌های ورودی و خروجی

| ویژگی | نسخه ۱۶ (مبنای اولیه) | نسخه ۱۷ (مبنای این اصلاحیه) | نسخه ۱۷.۱ (خروجی نهایی) |
|---|---|---|---|
| **نام بسته** | `CH01_CHESS_ANDROID_PROTOTYPE-16.zip` | `CH01_CHESS_ANDROID_PROTOTYPE-17.zip` | `CH01_CHESS_ANDROID_PROTOTYPE-17.1.zip` |
| **SHA-256 بسته** | `0183892be0d8bf1041e7d175f1b76dbb993dbd182f20619c78a27e77041aab8c` | `6c2800b45f0fc5178fa506a8ecaa1965c0b8eb7428f5aba48a88159c6a5af96e` | `82c46fac5b148960944a5890685dfe7ab77d1249b65de532b04b1bd817d25f2c` |
| **حجم فایل ZIP** | `~21.7 MB` (`24,966,769` بایت خام) | `2.72 MB` (`2,856,705` بایت) | **`2.73 MB` (`2,862,621` بایت — بسیار کمتر از سقف ۸ مگابایت)** |
| **تعداد کل فایل‌ها در بسته** | `149` فایل | `107` فایل | `109` فایل (`107` فایل نسخه ۱۷ + حفظ `debug.keystore` و `debug.keystore.base64` برای ساخت و نصب بی‌نقص APK Debug) |
| **مجوز Unix فایل `gradlew`** | `644` (`-rw-r--r--`) | `644` (`-rw-r--r--`) | **`755` (`-rwxr-xr-x`)** هم روی دیسک و هم در هدر Unix داخل ZIP |

---

## ۲. بررسی فنی `debug.keystore` و علت مانع ساخت/نصب APK روی گوشی

در بررسی دقیق چرخه ساخت و نصب APK موارد زیر مشخص و مستند شد:

1. **نحوه تأمین `debug.keystore` در محیط AI Studio در برابر بیلد مستقل Gradle:**
   - در فایل `app/build.gradle.kts` (خطوط ۳۲ تا ۳۷)، پیکربندی امضای آزمایشی (`signingConfigs.create("debugConfig")`) به‌طور صریح به فایل `${rootDir}/debug.keystore` اشاره می‌کند (`storeFile = file("${rootDir}/debug.keystore")`).
   - در محیط ابری AI Studio، این فایل پیش از اجرای بیلد به‌صورت خودکار از روی `debug.keystore.base64` بازگشایی و در ریشه پروژه ایجاد می‌شود.
   - اما در یک محیط استاندارد خارج از AI Studio (مانند Android Studio یا اجرای `./gradlew :app:assembleDebug` روی بسته استخراج‌شده)، پلاگین Android Gradle برای یک `signingConfig` سفارشی با نام `debugConfig` به‌صورت خودکار فایل `${rootDir}/debug.keystore` را تولید نمی‌کند.

2. **مانع بسته‌بندی و علت خطای نصب روی گوشی در صورت حذف `debug.keystore`:**
   - **مانع ساخت Debug (`assembleDebug`):** در بسته نسخه ۱۷ که فایل‌های `debug.keystore` و `debug.keystore.base64` حذف شده بودند، اجرای تست‌های واحد (`:app:testDebugUnitTest`) موفق بود، اما اجرای `./gradlew :app:assembleDebug` در پوشه استخراج‌شده در تسک `:app:validateSigningDebug` با خطای زیر متوقف می‌شد:
     `Execution failed for task ':app:validateSigningDebug'. > Keystore file '.../debug.keystore' not found for signing config 'debugConfig'.`
   - **علت عدم نصب بسته روی گوشی (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`):** هنگامی که پروژه بدون فایل‌های `debug.keystore` و `debug.keystore.base64` بین دو محیط کاری منتقل می‌شود، محیط جدید یک کلید Debug تازه با اثر انگشت گواهی (`SHA-1` / `SHA-256`) متفاوت تولید می‌کند. اگر نسخه قبلی برنامه (`applicationId = "com.aistudio.chessbook.chone"`) از قبل با امضای قبلی روی گوشی نصب باشد، سیستم‌عامل اندروید به دلیل **عدم تطابق امضای دیجیتال با بسته نصب‌شده قبلی** مانع نصب APK جدید روی گوشی می‌شود (`App not installed as package conflicts with an existing package`).

3. **راهکار اعمال‌شده در نسخه ۱۷.۱:**
   - طبق دستور، **تنظیمات امضای موجود (`signingConfigs`) در `app/build.gradle.kts` بدون هیچ تغییری حفظ شد**.
   - **هیچ کلید خصوصی Release (`my-upload-key.jks`)** در پروژه یا داخل بسته ZIP قرار داده نشد.
   - دو فایل استاندارد امضای آزمایشی (`debug.keystore` به حجم `2,650` بایت و `debug.keystore.base64` به حجم `3,536` بایت با گواهی استاندارد `CN=Android Debug, O=Android, C=US` و اثر انگشت `SHA-1: 30:54:73:81:70:4D:05:E7:C0:00:A5:80:C5:CE:6A:C5:1F:1F:94:A1`) در ریشه بسته `CH01_CHESS_ANDROID_PROTOTYPE-17.1.zip` حفظ شدند تا:
     - ساخت APK نسخه Debug (`./gradlew :app:assembleDebug`) بلافاصله پس از استخراج ZIP بدون هیچ مانعی انجام شود (`BUILD SUCCESSFUL`).
     - امضای دیجیتال APK در تمام بیلدهای بعدی کاملاً ثابت بماند (در صورتی که روی گوشی نسخه قدیمی با امضای متفاوت از جلسات قبل نصب است، کافی است یک بار نسخه قبلی از روی گوشی حذف/Uninstall شود تا نسخه جدید و تمام نسخه‌های بعدی بدون تداخل نصب گردند).

---

## ۳. بررسی ارجاعات و حذف فایل‌های تصویری و آرشیوی تکراری

پیش از حذف هر فایل از بسته تحویلی، تمام ارجاعات کد و تست‌ها در سراسر پروژه جست‌وجو شد (`SplashScreen.kt`, `IntroductionScreen.kt`, `ReaderPaginationRobolectricTest.kt`):

1. **منابع اصلی حفظ‌شده در برنامه (`1096 × 1435` پیکسل، بدون تغییر اندازه یا افت کیفیت):**
   - `app/src/main/res/drawable-nodpi/bg_splash_01.png` (`1,503,407` بایت — `1096 × 1435`، RGB 8-bit)
   - `app/src/main/res/drawable-nodpi/bg_intro_02.png` (`1,025,253` بایت — `1096 × 1435`، RGB 8-bit)
   - کدهای `SplashScreen.kt` و `IntroductionScreen.kt` و تست‌های `ReaderPaginationRobolectricTest.kt` صرفاً به `R.drawable.bg_splash_01` و `R.drawable.bg_intro_02` ارجاع دارند.

2. **تصاویر و فایل‌های تکراری حذف‌شده از بسته نهایی (مجموعاً ۶ تصویر تکراری + ۴ فایل ZIP قدیمی/مرجع = ۱۰ فایل غیرضروری):**
   - `app/src/main/assets/01.png` (`1.5 MB` — کپی تکراری بدون هیچ ارجاع در کد یا تست‌ها)
   - `app/src/main/assets/02.png` (`1002 KB` — کپی تکراری بدون هیچ ارجاع در کد یا تست‌ها)
   - پوشه `public/assets/aistudio/` شامل ۴ تصویر مرجع تکراری (`01_c6e1b016.png`, `01-2_824a7811.png`, `02_df36dd5e.png`, `02-1_ba40e670.png`) و فایل‌های آرشیو قدیمی داخل `public/` (`~3.4 MB`).

---

## ۴. بهینه‌سازی اسکرین‌شات‌های مستندات (`screenshots/`)

- **وضعیت نسخه ۱۶:** شامل **۳۸ فایل PNG** با حجم مجموع **۱۵٫۰ مگابایت** (`15,612 KB`).
- **تعداد اسکرین‌شات‌های حذف‌شده از بسته تحویلی:** **۳۲ اسکرین‌شات تکراری/مشابه حذف شدند** و **۶ اسکرین‌شات کلیدی باقی‌مانده به فرمت WebP (کیفیت ۸۰٪، حداکثر ضلع ۱۶۰۰ پیکسل، با حفظ کامل نسبت تصویر و خوانایی نوشته‌ها و بدون برش)** تبدیل شدند.
- **حجم نهایی کل پوشه `screenshots/` در بسته تحویلی:** **۱۷۳ کیلوبایت** (بسیار کمتر از سقف ۱٫۵ مگابایت کل پوشه و سقف ۲۵۰ کیلوبایت برای هر تصویر).
- **فهرست ۶ اسکرین‌شات WebP موجود در بسته نهایی:**
  1. `screenshots/06_board_normal_width_370dp.webp` (`43 KB` — `759 × 1600`): تخته شطرنج چوبی و طراحی مهره‌ها.
  2. `screenshots/12_educational_replay_ghost_and_arrow.webp` (`11 KB` — `738 × 1600`): نمایش Move Trail و شبح مهره هم‌رنگ در خانه مبدأ.
  3. `screenshots/16_persistent_move_description_after_trail_fade.webp` (`31 KB` — `738 × 1600`): نوار توضیح پایدار آخرین حرکت در پایین تخته پس از محو شدن مسیر حرکت.
  4. `screenshots/17_capture_pulse_radial_mid_animation.webp` (`8.8 KB` — `738 × 1600`): افکت پالس دایره‌ای قرمز گرادیانی (`Capture Pulse`) روی خانه مهره زده‌شده.
  5. `screenshots/18_bd032_board_stability_wrong_move.webp` (`39 KB` — `738 × 1600`): ثبات کامل ابعاد تخته در تمرین تعاملی `BD-032` هنگام نمایش پیام خطا و دکمه تلاش دوباره.
  6. `screenshots/19_reader_narration_bar_audio_not_ready.webp` (`41 KB` — `738 × 1600`): نوار کنترل صوتی کتاب (`ReaderNarrationBar`) در بالای صفحه مطالعه.

---

## ۵. شمارش دقیق و واقعی فایل‌های حفظ‌شده در بسته نهایی (`۱۰۹ فایل`)

تفکیک دقیق تمام **۱۰۹ فایل** موجود در `CH01_CHESS_ANDROID_PROTOTYPE-17.1.zip`:

1. **فایل‌های Kotlin سورس اصلی (`28 فایل` در `app/src/main/java/com/example/`):**
   - هسته شطرنج (`4 فایل`): `ChessEngine.kt`, `ChessRulesAdapter.kt`, `FenParser.kt`, `PgnReplayer.kt`
   - سیستم صوتی دوگانه (`2 فایل`): `ChessSoundManager.kt`, `BookNarrationManager.kt`
   - مدل‌ها (`3 فایل`): `BoardTransition.kt`, `ChapterModels.kt`, `ReaderPageModels.kt`
   - داده و تنظیمات (`2 فایل`): `ChapterRepository.kt`, `UserPreferencesManager.kt`
   - ویومدل و اکتیویتی (`2 فایل`): `MainActivity.kt`, `ChapterViewModel.kt`
   - کامپوننت‌های UI (`5 فایل`): `AudioSettingsDialog.kt`, `AuditBadge.kt`, `Bd032ExerciseView.kt`, `ChessBoardView.kt`, `PgnControlsView.kt`
   - صفحات UI (`5 فایل`): `AuditReportScreen.kt`, `IntroductionScreen.kt`, `ReaderScreen.kt`, `SectionsListScreen.kt`, `SplashScreen.kt`
   - تم و طراحی (`5 فایل`): `AppIcons.kt`, `Color.kt`, `ReaderDesignTokens.kt`, `Theme.kt`, `Type.kt`
2. **فایل‌های تست Kotlin (`5 فایل` — مجموع کل فایل‌های `.kt` در پروژه = `33 فایل`):**
   - تست‌های واحد، Robolectric و Compose UI (`4 فایل` در `app/src/test/java/com/example/`):
     - `ExampleRobolectricTest.kt` (۶۰ تست)
     - `ReaderPaginationRobolectricTest.kt` (۳۲ تست)
     - `ChessChapterOneTest.kt` (۲۳ تست)
     - `ExampleUnitTest.kt` (۱ تست)
   - تست قالب اندروید (`1 فایل` در `app/src/androidTest/java/com/example/`): `ExampleInstrumentedTest.kt`
3. **مانیفست و منابع اندروید (`36 فایل`):**
   - مانیفست برنامه (`1 فایل`): `app/src/main/AndroidManifest.xml`
   - منابع `app/src/main/res/` (`32 فایل`):
     - `drawable-nodpi/` (`2 فایل`): `bg_splash_01.png` و `bg_intro_02.png` (`1096 × 1435` پیکسل، دست‌نخورده)
     - `raw/` (`11 فایل`): ۱۱ فایل صوتی WAV استودیویی شطرنج (`snd_*.wav`)
     - `drawable/` (`2 فایل`): `ic_launcher_background.xml`, `ic_launcher_foreground.xml`
     - `mipmap-anydpi-v26/` (`2 فایل`): `ic_launcher.xml`, `ic_launcher_round.xml`
     - `mipmap-mdpi` تا `mipmap-xxxhdpi` (`10 فایل`): آیکون‌های پیکسلی لانچر
     - `values/` (`3 فایل`): `strings.xml`, `colors.xml`, `themes.xml`
     - `xml/` (`2 فایل`): `backup_rules.xml`, `data_extraction_rules.xml`
   - دارایی‌های `app/src/main/assets/` (`3 فایل`): `chapter_01_diagram_data.json`, `chapter_01_pgn_steps.json`, `narration/manifest.json`
4. **ابزار ساخت، تنظیمات Gradle و کلید امضای Debug (`16 فایل`):**
   - `gradlew` (با مجوز اجرایی `755` / `-rwxr-xr-x`), `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.gitignore`, `app/.gitignore`, `.env.example`, `metadata.json`, `debug.keystore`, `debug.keystore.base64`
5. **مستندات ریشه پروژه (`5 فایل`):**
   - `README.md`, `IMPLEMENTATION_REPORT.md`, `THIRD_PARTY_NOTICES.md`, `CHAPTER1_DATA_AUDIT.md`, `PACKAGING_OPTIMIZATION_REPORT.md`
6. **فایل‌های مرجع Starter Pack (`7 فایل` در `starter_pack/`):**
   - `BOOK_ASSET_INVENTORY_CH01_UPDATED.xlsx`, `CHAPTER_01_DIAGRAM_DATA.json`, `CHAPTER_01_DIAGRAM_MANIFEST.csv`, `CHAPTER_01_IMPLEMENTATION_SPEC.md`, `CHAPTER_01_REPLAY_GAMES.pgn`, `GEMINI_PROMPT_CH1_PROTOTYPE.md`, `START_HERE.txt`
7. **اسکرین‌شات‌های بهینه‌شده مستندات (`6 فایل` WebP در `screenshots/`)**
8. **گزارش‌های تست آخرین اجرای واقعی (`6 فایل` در `test-reports/`):**
   - `TEST-com.example.ExampleRobolectricTest.xml` (۶۰ تست)
   - `TEST-com.example.ReaderPaginationRobolectricTest.xml` (۳۲ تست)
   - `TEST-com.example.ChessChapterOneTest.xml` (۲۳ تست)
   - `TEST-com.example.ExampleUnitTest.xml` (۱ تست)
   - `xml_test_summary.txt`
   - `raw-test-execution-log.txt`

> **جمع کل فایل‌ها:** `28 + 5 + 36 + 16 + 5 + 7 + 6 + 6 = 109 فایل`

---

## ۶. نتایج اجرای واقعی تست‌ها و ساخت APK روی بسته استخراج‌شده

بسته در پوشه خالی استخراج شد و هر دو فرمان اجرای تست و ساخت APK نسخه Debug روی پروژه استخراج‌شده اجرا و تأیید گردیدند:

```bash
./gradlew :app:testDebugUnitTest --rerun-tasks --console=plain
./gradlew :app:assembleDebug --console=plain
```

- **نتیجه اجرای تست‌ها:** `BUILD SUCCESSFUL` — **۱۱۶ تست موفق (`0 failures`, `0 errors`, `0 skipped`)**:
  - `com.example.ExampleRobolectricTest`: **۶۰ تست موفق**
  - `com.example.ReaderPaginationRobolectricTest`: **۳۲ تست موفق**
  - `com.example.ChessChapterOneTest`: **۲۳ تست موفق**
  - `com.example.ExampleUnitTest`: **۱ تست موفق**
- **نتیجه ساخت APK نسخه Debug (`:app:assembleDebug`):** `BUILD SUCCESSFUL` (امضاشده با `debug.keystore` و تأییدشده با `apksigner verify` و `zipalign`).
