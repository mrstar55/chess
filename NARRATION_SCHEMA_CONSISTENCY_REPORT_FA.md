# گزارش یکسان‌سازی مسیر فایل صوتی و تفکیک اعتبارسنجی ساختاری و معنایی Narration Manifest — نسخه ۱۹.۳ (`CH01_CHESS_ANDROID_PROTOTYPE-19.3.zip`)

## ۱. مشخصات فایل ورودی و خروجی

| ویژگی | مقدار |
|---|---|
| **فایل ورودی (مبنا)** | `CH01_CHESS_ANDROID_PROTOTYPE-19.2.zip` |
| **SHA-256 فایل ورودی** | `5f657bf7308fad5fdcb338a577d253ed6f7d435902e4f94e69ff3fd5e3705cc0` |
| **حجم فایل ورودی** | `4,253,848` بایت (`4.06 MiB`) |
| **فایل خروجی نهایی** | `CH01_CHESS_ANDROID_PROTOTYPE-19.3.zip` |

---

## ۲. قرارداد رسمی و واحد مسیر فیزیکی فایل صوتی (`assets/narration/`)

### الف) محل رسمی ذخیره‌سازی فایل‌های صوتی کتاب
- **پوشهٔ رسمی پایه:** `app/src/main/assets/narration/`
- **مقدار `audioAssetPath` در `manifest.json`:** همیشه به‌صورت مسیر نسبی امن نسبت به پوشهٔ `app/src/main/assets/narration/` تعریف می‌شود.
  - **مثال مقدار در مانیفست:** `"chapter_01/section_01/block_001.mp3"`
  - **مسیر واقعی فایل در پروژه:** `app/src/main/assets/narration/chapter_01/section_01/block_001.mp3`
  - **مسیر بازگشایی در Android Runtime (`BookNarrationManager.kt`):** `appContext.assets.open("narration/$assetRelativePath")` و `appContext.assets.openFd("narration/$assetRelativePath")`

### ب) اصلاح انجام‌شده در `app/build.gradle.kts` (`resolveAudioFile()`)
در نسخهٔ ۱۹.۲ تابع `resolveAudioFile()` در تسک `validateContentPacks` سه مسیر (`assets/narration/$audioPath`، `assets/$audioPath` و `chapterFolder/$audioPath`) را می‌پذیرفت که می‌توانست باعث تأیید فایلی در زمان بیلد شود که در Android Runtime قابل باز شدن نیست.

در نسخهٔ **۱۹.۳**:
1. تنها مسیر مجاز در `resolveAudioFile(audioPath)` عبارت است از:
   `File(assetsDir, "narration/$audioPath")`
2. fallback به `File(assetsDir, audioPath)` به‌طور کامل حذف شد.
3. fallback به `File(chapterFolder, audioPath)` به‌طور کامل حذف شد.
4. مسیر Canonical فایل (`candidate.canonicalFile.path`) بررسی می‌شود تا حتماً زیر پوشهٔ `File(assetsDir, "narration").canonicalFile` قرار داشته باشد و هیچ فایلی خارج از `assets/narration/` پذیرفته نشود.
5. متد کمکی هم‌تراز `NarrationManifestValidator.resolveAudioFileInAssetsDir(assetsDir, audioAssetPath)` نیز با همین منطق دقیق Canonical برای استفاده در تست‌های مبتنی بر فایل‌سیستم اضافه شد.

---

## ۳. تفکیک قطعی مسئولیت‌های سه لایهٔ اعتبارسنجی

در نسخهٔ ۱۹.۳ مرز دقیق میان اعتبارسنجی ساختاری (Structural)، اعتبارسنجی معنایی زمان اجرا (Semantic Runtime) و اعتبارسنجی بستهٔ محتوا در زمان ساخت (Gradle Build-time) به شرح زیر تفکیک و در کد تست و مستندات اعمال شد:

### لایهٔ اول: JSON Schema Structural Validation (`content_schemas/narration_manifest.schema.json`)
این لایه صرفاً ساختار و نحو سند JSON را بدون آگاهی از فایل‌سیستم یا سایر فایل‌های JSON فصل بررسی می‌کند:
- وجود فیلدهای اجباری (`required`) در هر ۴ سطح (`schemaVersion`, `chapters`, `chapterId`, `sections`, `sectionId`, `blocks`, `blockId`, `audioAssetPath`, `status`)
- عدم وجود هرگونه فیلد ناشناخته (`additionalProperties: false`)
- نوع داده‌ها (`object`, `array`, `string`, `integer`) و مقدار ثابت `schemaVersion: 1`
- الگوی شناسه‌ها (`Identifier`: `^[A-Za-z0-9_\-]{1,128}$` و عدم وجود زیررشتهٔ مصنوعی `__part_`)
- الگوی مسیر نسبی امن فایل صوتی (`SafeAudioAssetPath`: `^[A-Za-z0-9_\-]+(/[A-Za-z0-9_\-]+)*\.(mp3|wav|ogg|m4a|aac)$`)
- مقادیر مجاز `status` (`"AVAILABLE"` یا `"UNAVAILABLE"`)
- عدد صحیح نامنفی بودن `durationMs` (`integer`, `minimum: 0`)
- قانون استاندارد `uniqueItems: true` در JSON Schema که صرفاً دو شیء JSON کاملاً یکسان را در یک آرایه رد می‌کند (و دو شیء متفاوت با `blockId` یکسان ولی `audioAssetPath` متفاوت را رد نمی‌کند).

> **نکتهٔ فنی دربارهٔ `evaluateAgainstSchemaFile()` در `ContentArchitectureVersion19Test.kt`:**
> این تابع یک ارزیاب دستی ساختاری در کد تست است که قوانین ساختاری تعریف‌شده در `content_schemas/narration_manifest.schema.json` را می‌خواند و اجرا می‌کند و یک کتابخانهٔ خارجی JSON Schema Validator نیست. در نسخهٔ ۱۹.۳ این تابع اصلاح شد تا هیچ قانون معنایی (مانند یکتایی کلید `blockId` در اشیای غیرهمسان) را به نام JSON Schema رد نکند.

### لایهٔ دوم: Kotlin Semantic Validation (`NarrationManifestValidator.validateManifestStructure`)
این لایه قوانین معنایی و ارتباطی را روی ساختار مانیفست بررسی می‌کند:
- تشخیص شناسه‌های تکراری (`chapterId` تکراری، `sectionId` تکراری در فصل، `blockId` تکراری در بخش یا کل فصل حتی اگر سایر فیلدهای دو بلوک متفاوت باشند)
- تطابق `chapterId` مانیفست با شناسهٔ فصل مالک (`expectedChapterId`)
- تعلق `sectionId` به بخش‌های واقعی تعریف‌شده در `chapter.json` همان فصل
- تعلق `blockId` به بلوک‌های واقعی تعریف‌شده در `orderedContentBlocks` همان بخش
- بررسی وجود فیزیکی فایل صوتی در مسیر `assets/narration/$audioAssetPath` هنگامی که `status == "AVAILABLE"` است

### لایهٔ سوم: Gradle Content Pack Validation (`:app:validateContentPacks`)
این تسک در زمان بیلد واقعی روی بسته‌های محتوای موجود در `app/src/main/assets/books/` اجرا می‌شود:
- بررسی وجود فیزیکی فایل صوتی منحصراً در `File(assetsDir, "narration/$audioPath")` برای بلوک‌های دارای `status == "AVAILABLE"`
- بررسی تطابق کامل مانیفست صوتی با `chapter.json` و فایل‌های `sections/*.json` همان بستهٔ محتوا
- توقف بیلد با پیام خطای دقیق `[validateContentPacks] [file] [chapterId] [sectionId] [blockId]` در صورت هرگونه مغایرت ساختاری، معنایی یا نبود فایل صوتی `AVAILABLE`

---

## ۴. جدول نتایج ۸ سناریوی الزامی مسیر فایل صوتی (`assets/narration/`)

در تست `v19_1 narration 2` (بخش Scenario 7) ساختار واقعی پوشهٔ `assets/narration/` شبیه‌سازی و هر ۸ سناریوی مسیر فایل صوتی به همراه تطابق مسیر در `FakeNarrationPlaybackEngine` راستی‌آزمایی شد:

| ردیف | سناریوی مسیر فایل صوتی | محل فیزیکی فایل در Fixture | وضعیت در مانیفست | نتیجهٔ اعتبارسنجی | وضعیت تست |
|---|---|---|---|---|---|
| ۱ | فایل موجود در مسیر رسمی `assets/narration/` | `assets/narration/ch01/sec01_intro.wav` | `AVAILABLE` | **قبول (Valid)** + تطابق کامل مسیر در `FakeNarrationPlaybackEngine` | **PASS** |
| ۲ | فایل فقط در ریشهٔ `assets/` قرار دارد | `assets/ch01/only_in_assets_root.wav` | `AVAILABLE` | **رد (Invalid)** — عدم پذیرش خارج از `narration/` | **PASS** |
| ۳ | فایل فقط در پوشهٔ فصل `CH01/` قرار دارد | `assets/books/.../CH01/ch01/only_in_ch01.wav` | `AVAILABLE` | **رد (Invalid)** — عدم پذیرش خارج از `narration/` | **PASS** |
| ۴ | فایل در پوشهٔ دیگر خارج از `narration/` با نام معتبر | `assets/other_audio/valid_name.mp3` | `AVAILABLE` | **رد (Invalid)** — عدم پذیرش خارج از `narration/` | **PASS** |
| ۵ | بلوک با وضعیت `UNAVAILABLE` بدون فایل فیزیکی | *(فایل وجود ندارد)* | `UNAVAILABLE` | **قبول (Valid)** | **PASS** |
| ۶ | مسیر دارای `../` (Path Traversal) | `../ch01/only_in_assets_root.wav` | `AVAILABLE` | **رد (Invalid)** | **PASS** |
| ۷ | مسیر مطلق | `/narration/ch01/sec01_intro.wav` | `AVAILABLE` | **رد (Invalid)** | **PASS** |
| ۸ | نبود فایل فیزیکی در `assets/narration/` با وضعیت `AVAILABLE` | *(فایل وجود ندارد)* | `AVAILABLE` | **رد (Invalid)** — خطای اعتبارسنجی و بیلد | **PASS** |

---

## ۵. جدول اصلاح‌شدهٔ ۱۸ سناریوی Fixture در تست واحد (`v19_2 narration 3`)

این ۱۸ سناریوی Fixture در تست واحد Kotlin توسط **ارزیاب ساختاری Schema (`evaluateAgainstSchemaFile`)** و **اعتبارسنج معنایی Kotlin (`NarrationManifestValidator.validateManifestStructure`)** با فایل‌های صوتی قرارگرفته در `tempAssetsDir/narration/...` اجرا می‌شوند (تسک Gradle `validateContentPacks` به‌طور جداگانه روی Content Pack واقعی پروژه اجرا می‌گردد):

| ردیف | عنوان سناریو (Fixture) | لایهٔ اول: JSON Schema (Structural) | لایهٔ دوم: Kotlin Validator (Semantic + `assets/narration/`) | دلیل تفاوت لایهٔ ساختاری و معنایی | وضعیت تست |
|---|---|---|---|---|---|
| ۱ | Manifest خالی معتبر (`chapters: []`) | **قبول** | **قبول** | ساختار و معنا هر دو معتبر | **PASS** |
| ۲ | Manifest معتبر با ۱ فصل و ۱ بخش (`blocks: []`) | **قبول** | **قبول** | ساختار و معنا هر دو معتبر | **PASS** |
| ۳ | Manifest معتبر با چند فصل (`CH01`, `CH02`) | **قبول** | **قبول** | ساختار و معنا هر دو معتبر | **PASS** |
| ۴ | Manifest معتبر با چند بخش و چند بلوک | **قبول** | **قبول** | فایل‌ها در `assets/narration/` موجودند | **PASS** |
| ۵ | بلوک معتبر با فایل صوتی موجود در `assets/narration/` | **قبول** | **قبول** | ساختار معتبر و فایل فیزیکی موجود | **PASS** |
| ۶ | بلوک دارای `status: "UNAVAILABLE"` بدون فایل فیزیکی | **قبول** | **قبول** | در حالت `UNAVAILABLE` نبود فایل مجاز است | **PASS** |
| ۷ | `durationMs` منفی (`-250`) | **رد** | **رد** | نقض `minimum: 0` در Schema و شرط `>= 0` در Kotlin | **PASS** |
| ۸ | `chapterId` خالی (`""`) | **رد** | **رد** | نقض الگوی `Identifier` در هر دو لایه | **PASS** |
| ۹ | `sectionId` خالی (`""`) | **رد** | **رد** | نقض الگوی `Identifier` در هر دو لایه | **PASS** |
| ۱۰ | **`blockId` تکراری با `audioAssetPath` متفاوت** | **قبول** | **رد** | در JSON Schema دو شیء بلوک یکسان نیستند (`uniqueItems` فقط اشیای کاملاً برابر را رد می‌کند)، اما لایهٔ معنایی تکرار `blockId` را رد می‌کند | **PASS** |
| ۱۱ | مسیر دارای `../` (`chapter_01/../secret.mp3`) | **رد** | **رد** | نقض الگوی `SafeAudioAssetPath` و منع Path Traversal | **PASS** |
| ۱۲ | مسیر مطلق (`/sdcard/chapter_01/audio_001.mp3`) | **رد** | **رد** | نقض الگوی مسیر نسبی در هر دو لایه | **PASS** |
| ۱۳ | مسیر URL (`https://example.com/audio_001.mp3`) | **رد** | **رد** | نقض الگوی مسیر نسبی در هر دو لایه | **PASS** |
| ۱۴ | **فایل صوتی ناموجود با وضعیت `AVAILABLE`** | **قبول** | **رد** | نحو مسیر در Schema معتبر است، اما لایهٔ معنایی/فایل‌سیستمی نبود فایل در `assets/narration/` را رد می‌کند | **PASS** |
| ۱۵ | پسوند فایل صوتی نامعتبر (`audio_001.flac`) | **رد** | **رد** | پسوند خارج از `(mp3\|wav\|ogg\|m4a\|aac)` در هر دو لایه رد می‌شود | **PASS** |
| ۱۶ | ساختار قدیمی ناسازگار (`segments` / `asset` / `audioAvailable`) | **رد** | **رد** | نقض `additionalProperties: false` و نبود آرایهٔ اجباری `chapters` | **PASS** |
| ۱۷ | **`chapterId` نامنطبق با فصل مالک (`CH05_OTHER` برای `CH01`)** | **قبول** | **رد** | شناسه از نظر نحوی در Schema معتبر است، اما لایهٔ معنایی عدم تطابق با فصل مالک را رد می‌کند | **PASS** |
| ۱۸ | **`sectionId` متعلق به فصل دیگر (`SEC_CH99_FOREIGN_SECTION`)** | **قبول** | **رد** | شناسه از نظر نحوی در Schema معتبر است، اما لایهٔ معنایی عدم عضویت در بخش‌های فصل را رد می‌کند | **PASS** |

---

## ۶. نتایج اجرای جداگانهٔ دستورات Gradle Validation، Unit/Robolectric و Build

| ردیف | دستور اجراشده | وضعیت | نتیجهٔ دقیق |
|---|---|---|---|
| ۱ | `./gradlew :app:validateContentPacks --console=plain` | **BUILD SUCCESSFUL** | اعتبارسنجی کامل بستهٔ محتوای واقعی پروژه (`CH01`) با مسیر رسمی `assets/narration/` بدون خطا |
| ۲ | `./gradlew :app:testDebugUnitTest --rerun-tasks --console=plain` | **BUILD SUCCESSFUL** | **۱۲۸ تست اجرا شد: ۱۲۸ موفق (Pass)، ۰ شکست (Fail)، ۰ خطا (Error)، ۰ ردشده (Skipped)** |
| ۳ | `./gradlew :app:assembleDebug --console=plain` | **BUILD SUCCESSFUL** | ساخت کامل APK نسخهٔ Debug بدون خطا |

### تفکیک تعداد تست‌ها بر اساس فایل‌های XML واقعی در `test-reports/`

| کلاس تست | تعداد تست | Pass | Fail | Error | Skipped |
|---|---|---|---|---|---|
| `ExampleRobolectricTest` | ۶۰ | ۶۰ | ۰ | ۰ | ۰ |
| `ReaderPaginationRobolectricTest` | ۳۲ | ۳۲ | ۰ | ۰ | ۰ |
| `ChessChapterOneTest` | ۲۳ | ۲۳ | ۰ | ۰ | ۰ |
| `ContentArchitectureVersion19Test` | ۷ | ۷ | ۰ | ۰ | ۰ |
| `ContentArchitectureVersion18Test` | ۵ | ۵ | ۰ | ۰ | ۰ |
| `ExampleUnitTest` | ۱ | ۱ | ۰ | ۰ | ۰ |
| **مجموع کل** | **۱۲۸** | **۱۲۸** | **۰** | **۰** | **۰** |
