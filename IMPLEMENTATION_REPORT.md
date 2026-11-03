# گزارش فنی جامع پیاده‌سازی — نسخه ۱۶ (Version 16)
## تثبیت نمایش حرکت، افکت حرفه‌ای Capture، ثبات اندازه تخته و زیرساخت خواندن صوتی کتاب

---

## ۱. فایل مبنا و مشخصات SHA-256

- **فایل مبنای ورودی:** `CH01_CHESS_ANDROID_PROTOTYPE-15.zip`
- **SHA-256 فایل مبنا:** `8dab4fb3bc2df8b649e777e9cd8ad02cfdc05ac43c3da71aed27ea58988d8934`
- **نسخه خروجی تحویلی:** `CH01_CHESS_ANDROID_PROTOTYPE-16.zip`
- **SHA-256 بسته تحویلی نهایی (`CH01_CHESS_ANDROID_PROTOTYPE-16.zip`):** `47a3e1ab5ecd966a4fd94ca62b84b19f29d4eaed06a7ae3037e1bb1e5c132e86`

تمامی قابلیت‌های تأییدشده نسخه‌های قبلی شامل موتور شطرنج (`ChessEngine`, `ChessRulesAdapter`, `FenParser`, `PgnReplayer`), سیستم صوتی ۱۱گانه شطرنج (`ChessSoundManager`), ده نمودار فصل اول (`BD-025` تا `BD-034`), صفحه‌بندی راست‌به‌چپ فارسی (`ReaderPaginator`), صفحات آغازین (`SplashScreen`, `IntroductionScreen`, `SectionsListScreen`) و طراحی تخته چوبی (`ChessBoardView`) بدون تغییر در منطق پایه حفظ شده‌اند.

---

## ۲. فهرست دقیق فایل‌های اضافه‌شده و تغییریافته در نسخه ۱۶

### الف) فایل‌های جدید اضافه‌شده
1. `app/src/main/java/com/example/audio/BookNarrationManager.kt` — مدیر مستقل خواندن صوتی کتاب (`BookNarrationManager`), مدل مانیفست صوتی (`NarrationManifest`, `SectionNarrationManifest`), اعتبارسنج مسیرهای صوتی، صف‌پرداز بلوک‌های متنی فصل اول با ادغام قطعات صفحه‌بندی‌شده (`ReaderPaginator.originalBlockId`) و موتور پخش قابل تست (`NarrationMediaPlayerEngine`, `AndroidAssetMediaPlayerEngine`).
2. `app/src/main/assets/narration/manifest.json` — مانیفست رسمی، معتبر و خالی زیرساخت خواندن صوتی برای هر ۶ بخش واقعی فصل اول (`CH01_SEC_01_OPENING` تا `CH01_SEC_06_SUMMARY`).

### ب) فایل‌های تغییریافته
3. `app/src/main/java/com/example/model/BoardTransition.kt` — افزودن ثابت `CAPTURE_PULSE_DURATION_MS = 380`، تابع `pieceColorFa(isWhite)` و سازنده متن پایدار حرکت فارسی با ذکر رنگ مهره (`buildPersistentMoveDescriptionFa`) برای انواع حرکت‌های عادی، زدن مهره، کیش، مات، قلعه کوتاه/بلند، آن‌پاسان و ارتقای پیاده.
4. `app/src/main/java/com/example/ui/components/ChessBoardView.kt` — حذف کامل `move_trail_caption` از داخل تخته، افزودن کامپوننت مستقل `PersistentMoveDescriptionBar` با ارتفاع ثابت `36.dp` و `Crossfade` در پایین و بیرون تخته، حذف کامل کادرها و نشانگرهای خانه مبدأ و مقصد (`trail_source_marker_*`, `trail_dest_glow_*`, `trail_ep_captured_marker_*`), هم‌رنگ‌سازی شبح مهره (`GhostTintFillTeal = #438F8A`) با فلش مسیر حرکت و اعمال شفافیت (`~28%`) فقط یک‌بار، حذف کامل حلقه‌ها و کادرهای توخالی قرمز قبلی (`capture_indicator_*`, `capture_impact_highlight_*`) و پیاده‌سازی پالس دایره‌ای قرمز گرادیانی ۴مرحله‌ای (`capture_pulse_<square>`, `computeCapturePulseMetrics`) روی خانه واقعی مهره زده‌شده در لایه زیرین مهره‌ها.
5. `app/src/main/java/com/example/ui/components/Bd032ExerciseView.kt` — جداسازی کامل ابعاد تخته شطرنج از وضعیت‌های متغیر تمرین با اختصاص اسلات‌های ارتفاع ثابت برای عنوان (`20.dp`), دستورالعمل (`28.dp`), نوار توضیح حرکت پایدار (`36.dp`), باکس پیام بازخورد (`34.dp`) و ردیف دکمه‌های کنترلی (`34.dp`).
6. `app/src/main/java/com/example/ui/ChapterViewModel.kt` — اتصال وضعیت `lastMoveDescriptionFa` به `PgnDiagramUiState` و `Bd032UiState` (مستقل از `showMoveTrail` و پاک‌شدن خودکار در Reset/Undo/Retry/تغییر صفحه یا بخش) و اتصال کامل کنترل‌های `BookNarrationManager` (حالت پخش صفحه فعلی، حالت پخش پیوسته فصل اول با ورق‌زدن خودکار صفحات، مکث، ادامه، توقف، تغییر سرعت `0.8x`/`1.0x`/`1.2x` و توقف خودکار در تغییر بخش/تب یا خروج از صفحه مطالعه).
7. `app/src/main/java/com/example/ui/screens/ReaderScreen.kt` — افزودن نوار کنترل جمع‌وجور خواندن صوتی (`ReaderNarrationBar` با تگ `reader_narration_bar`), نمایش `PersistentMoveDescriptionBar` بلافاصله زیر تخته در نمودارهای دارای بازپخش PGN، برجسته‌سازی بصری بلوک متنی در حال خوانده‌شدن (`narrated_active_block_<originalBlockId>`), افزودن تنظیمات مستقل خواندن صوتی در دیالوگ تنظیمات (`narration_enabled_switch`, `narration_speed_0_8x`, `narration_speed_1_0x`, `narration_speed_1_2x`) و تثبیت کامل `diagramNonBoardChromeDp` برای تمرین `BD-032` (`244.dp`).
8. `app/src/main/java/com/example/data/UserPreferencesManager.kt` — ذخیره‌سازی پایدار تنظیمات مستقل خواندن صوتی کتاب (`narrationEnabled`, `narrationSpeed`, `narrationAutoTurnPage`, `narrationLastSectionId`, `narrationLastBlockId`, `narrationLastPositionMs`) کاملاً جدا از `soundEnabled` و `soundVolume` مربوط به افکت‌های صوتی شطرنج.
9. `app/src/main/java/com/example/ui/theme/AppIcons.kt` — افزودن آیکون‌های برداری استاندارد `PlayArrow`, `Pause` و `Stop` برای نوار کنترل صوتی کتاب.
10. `app/src/test/java/com/example/ExampleRobolectricTest.kt` — به‌روزرسانی تست‌های موجود مطابق قرارداد نسخه ۱۶ و افزودن ۶ تست جامع جدید (`v16 test A`, `v16 test B`, `v16 test C`, `v16 test D`, `v16 test E1`, `v16 test E2`) همراه با تولید اسکرین‌شات‌های واقعی `16` تا `20`.

---

## ۳. توضیح پیاده‌سازی Persistent Move Description (توضیح پایدار آخرین حرکت)

- **حذف از داخل تخته و انتقال به بیرون و پایین تخته:** کامپوننت قبلی `move_trail_caption` که به چرخه عمر `MoveTrailState` وابسته بود از داخل `ChessBoardView` حذف شد و کامپوننت مستقل `PersistentMoveDescriptionBar` با تگ `persistent_move_description` بلافاصله در پایین تخته شطرنج (هم در نمودارهای PGN در `ReaderScreen` و هم در تمرین تعاملی `Bd032ExerciseView`) قرار گرفت.
- **چرخه عمر پایدار و مستقل از Move Trail:**
  - متن آخرین حرکت در فیلد `lastMoveDescriptionFa` درون `PgnDiagramUiState` و `Bd032UiState` نگهداری می‌شود.
  - پس از محو شدن فلش و شبح Move Trail (`onPgnMoveTrailFinished` / `onBd032MoveTrailFinished`), متن `lastMoveDescriptionFa` **حذف نمی‌شود** و تا زمان انجام حرکت بعدی یا عملیات پاک‌کننده روی صفحه باقی می‌ماند.
  - در صورت انجام حرکت جدید رو به جلو، متن بدون پرش ارتفاع (با `Crossfade(tween(160))`) به حرکت جدید به‌روزرسانی می‌شود.
  - در صورت `Reset` (`resetPgn` / `resetBd032`), `Undo` (`stepPgnPrev`), `Retry` (`retryBd032`) یا تغییر صفحه و بخش، متن بلافاصله پاک (`null`) می‌شود.
  - انتخاب صرف مهره (`selectedSquare`) یا تلاش برای حرکت غیرقانونی در `BD-032` هرگز متن توضیح حرکت قبلی را تغییر نمی‌دهد یا متن کاذب ثبت نمی‌کند.
  - حتی اگر کاربر گزینه «نمایش مسیر حرکت مهره» (`showMoveTrail`) را خاموش کند، نوار توضیح حرکت پایدار همچنان آخرین حرکت انجام‌شده را نمایش می‌دهد.
- **ثبات کامل چیدمان (Zero Layout Shift):** `PersistentMoveDescriptionBar` دارای ارتفاع رزروشده ثابت `36.dp` است؛ بنابراین خالی بودن، ظاهر شدن یا تغییر متن از یک خط به دو خط، هیچ تغییری در ابعاد تخته شطرنج یا مکان دکمه‌های کنترلی ایجاد نمی‌کند.

---

## ۴. توضیح بازطراحی Move Trail و شبح مهره هم‌رنگ فلش

- **حذف کامل نشانگرها و کادرهای خانه‌ها:** تمامی کادرها و هایلایت‌های خانه در لایه Move Trail شامل `trail_source_marker_<square>`, `trail_dest_glow_<square>` و `trail_ep_captured_marker_<square>` به‌طور کامل از `ChessBoardView.kt` حذف شدند.
- **نقطه شروع و پایان دقیق فلش:** بردار فلش مسیر حرکت دقیقاً از مرکز هندسی خانه مبدأ (`centerOfSquare(fromSquare)`) آغاز شده و تا نزدیکی مرکز خانه مقصد امتداد می‌یابد (با عقب‌نشینی کنترل‌شده نوک فلش تا روی مهره مقصد را نپوشاند). در حرکت اسب، مسیر شکسته Lشکل از مرکز خانه مبدأ از طریق مرکز خانه میانی به سمت خانه مقصد رسم می‌شود و در قلعه (`O-O` / `O-O-O`) هر دو فلش شاه و رخ هم‌زمان رسم می‌شوند.
- **شبح مهره هم‌رنگ با فلش و اعمال یک‌باره Alpha:**
  - رنگ فلش مسیر حرکت و رنگ شبح مهره در خانه مبدأ از یک خانواده رنگی واحد (`GhostTintFillTeal = Color(0xFF438F8A)` و `GhostTintOutlineTeal = Color(0xFF215854)`) استفاده می‌کنند.
  - شفافیت شبح (`ghostAlpha = 0.28f * trailAlphaVal`) **صرفاً یک‌بار** در لایه رندر شبح اعمال می‌شود و تگ语义 `piece_tint_ghost_<square>_teal` صحت هم‌رنگی شبح با فلش را در تست‌ها اثبات می‌کند.
  - شبح مهره و فلش مسیر حرکت دقیقاً با یک منحنی زمانی مشترک (`trailAlphaVal`: نگه‌داشت `1200ms` پس از رسیدن مهره و محو نرم `500ms`) با هم ظاهر و با هم محو می‌شوند.

---

## ۵. توضیح پیاده‌سازی Red Capture Pulse (پالس دایره‌ای قرمز هنگام زده‌شدن مهره)

- **حذف کامل حلقه‌ها و کادرهای توخالی قرمز قبلی:** تمامی نشانگرهای قبلی شامل حلقه‌های توخالی (`Stroke`) و کادرهای چهارگوش قرمز دور خانه (`capture_indicator_*` و `capture_impact_highlight_*`) حذف شدند.
- **پالس دایره‌ای گرادیانی ۴مرحله‌ای (`capture_pulse_<square>`):**
  - هنگام وقوع هر حرکت Capture رو به جلو (`CAPTURE`, `PROMOTION_CAPTURE`, `EN_PASSANT`), یک انیمیشن مستقل `380ms` (`CAPTURE_PULSE_DURATION_MS`) روی خانه واقعی مهره زده‌شده اجرا می‌شود:
    - در Capture عادی و ارتقا با Capture: روی خانه مقصد (`toSquare`).
    - در حرکت آن‌پاسان (`En Passant`): دقیقاً روی خانه واقعی پیاده حریف (`capturedSquare`, مثلاً `d5`) نه خانه مقصد پیاده مهاجم (`d6`).
  - تابع `computeCapturePulseMetrics(progress)` چهار مرحله پیوسته را محاسبه می‌کند:
    1. **شروع ضربه (`0.00..0.15`):** شکل‌گیری پالس در مرکز خانه از شعاع `0.06` به `0.24` عرض خانه.
    2. **انبساط سریع (`0.15..0.55`):** گسترش سریع دایره تا پوشش حدود `88%` عرض خانه (`radiusFraction = 0.44f`, `centerAlpha = 0.84f`).
    3. **جمع‌شدن جزئی (`0.55..0.80`):** بازگشت نرم شعاع از اوج به `0.36f` و کاهش تدریجی شدت.
    4. **محو کامل (`0.80..1.00`):** کوچک‌شدن نهایی و رسیدن `alpha` به `0f`.
  - پالس به‌صورت دایره توپر گرادیانی (`Brush.radialGradient` با مرکز `#D9383A` و لبه شفاف `Color.Transparent`, بدون هیچ‌گونه `Stroke` یا کادر مربعی) در لایه زیرین مهره‌ها رسم می‌شود و هم‌زمان مهره زده‌شده با انیمیشن `340ms` (`scale: 1.0f -> 0.72f`, `alpha: 1.0f -> 0f`) محو می‌گردد.

---

## ۶. توضیح تثبیت اندازه تخته در تمرین تعاملی `BD-032`

- در `Bd032ExerciseView.kt` تمام بخش‌های غیرتخته دارای ظرف‌های با ارتفاع ثابت هستند:
  - عنوان تمرین: `Box(Modifier.fillMaxWidth().height(20.dp))`
  - دستورالعمل تمرین: `Box(Modifier.fillMaxWidth().height(28.dp))`
  - نوار توضیح حرکت پایدار (`PersistentMoveDescriptionBar`): `height(36.dp)`
  - ظرف پیام بازخورد (`exercise_feedback`): `Box(Modifier.fillMaxWidth().height(34.dp))` که چه زمانی که `feedbackMessage == null` باشد و چه زمانی که پیام راهنما، خطا یا موفقیت نمایش داده شود، دقیقاً `34.dp` فضا اشغال می‌کند.
  - ردیف دکمه‌های کنترلی پایین (`bd032_legal_hint_btn`, `bd032_retry_btn`, `exercise_reset_btn`): `Row(Modifier.fillMaxWidth().height(34.dp))` که ظاهر شدن یا مخفی شدن دکمه «تلاش دوباره» ارتفاع ردیف را تغییر نمی‌دهد.
- در `ReaderScreen.kt` نیز مقدار `diagramNonBoardChromeDp` برای تمرین تعاملی یک ثابت قطعی (`244.dp`) است که به هیچ متغیر وضعیتی وابسته نیست.
- تست `v16 test D` در هر سه عرض صفحه (`310dp`, `370dp`, `411dp`) ثابت بودن ۱۰۰٪ عرض و ارتفاع `chessboard_container` را در طول ۸ مرحله (حالت اولیه، انتخاب مهره `e5`, روشن‌کردن راهنمای حرکت‌های قانونی، حرکت غیرقانونی `e5->c4`, حرکت قانونی اشتباه `e5->e8` و نمایش پیام خطا + دکمه تلاش دوباره، فشردن تلاش دوباره، حل تمرین با `Qh8#` و شروع مجدد وضعیت) اثبات می‌کند.

---

## ۷. توضیح طراحی و پیاده‌سازی زیرساخت خواندن صوتی کتاب (`BookNarrationManager` و `manifest.json`)

- **ساختار دارایی‌ها و مانیفست (`app/src/main/assets/narration/manifest.json`):**
  - فایل `manifest.json` با `schemaVersion: 1` و `chapterId: "CH01"` شامل هر ۶ شناسه بخش واقعی فصل اول (`CH01_SEC_01_OPENING`, `CH01_SEC_02_CHECKMATE`, `CH01_SEC_03_RESIGNATION`, `CH01_SEC_04_TIMEOUT`, `CH01_SEC_05_ABandonment`, `CH01_SEC_06_SUMMARY`) با ساختار استاندارد و لیست خالی `blocks: []` ایجاد شده است.
- **اعتبارسنجی سخت‌گیرانه (`BookNarrationManager.isValidRelativeAssetPath`):**
  - مسیرهای صوتی باید نسبی، داخل `narration/` و دارای پسوند صوتی مجاز (`.mp3`, `.m4a`, `.ogg`, `.wav`, `.aac`) باشند.
  - مسیرهای مطلق (`/..`), پروتکل‌های اینترنتی (`http://`, `https://`, `file://`) و پیمایش دایرکتوری (`..`) رد می‌شوند.
  - شناسه‌های بخش و بلوک با مجموعه واقعی موجود در `ChapterRepository.loadSectionsFromAssets()` تطبیق داده می‌شوند.
- **اتصال به بلوک‌های متنی و جلوگیری از تکرار قطعات صفحه‌بندی‌شده:**
  - تنها بلوک‌های متنی قابل خواندن (`HeadingBlock`, `ParagraphBlock`, `FootnoteBlock`) وارد صف خواندن صوتی می‌شوند و بلوک‌های نمودار (`DiagramBlock`) از صف روایت متنی کنار گذاشته می‌شوند.
  - با استفاده از `ReaderPaginator.originalBlockId(block.blockId)`, اگر یک پاراگراف بلند توسط صفحه‌بند به چند قطعه (`P01`, `P01__part_2`) شکسته شده باشد، شناسه اصلی (`P01`) استخراج و یکتا می‌شود تا فایل صوتی آن پاراگراف هرگز دوباره از اول پخش نشود.
- **دو حالت پخش و کنترل‌های کامل:**
  - **حالت A (پخش صفحه فعلی — `playCurrentPage`):** فقط بلوک‌های متنی صفحه فعلی را به ترتیب در صف قرار می‌دهد و در انتهای صفحه متوقف می‌شود.
  - **حالت B (پخش پیوسته فصل اول — `playContinuousChapter`):** بلوک‌های متنی فصل اول را به ترتیب بخش و بلوک پخش می‌کند و هنگام ورود به بلوکی که در صفحه یا بخش بعدی قرار دارد، در صورت فعال بودن `autoTurnPage` به‌طور خودکار صفحه مطالعه را ورق می‌زند.
  - شامل کنترل‌های `Play`, `Pause`, `Resume`, `Stop`, `Seek` و انتخاب سرعت پخش (`0.8x`, `1.0x`, `1.2x`).
  - در صورت نبودن فایل‌های صوتی در نسخه فعلی، بدون هیچ‌گونه کرش یا خطا، وضعیت `UNAVAILABLE` و پیام فارسی محترمانه `«فایل صوتی این صفحه هنوز آماده نشده است»` در نوار صوتی نمایش داده می‌شود.

---

## ۸. جداسازی کامل صدای شطرنج (`ChessSoundManager`) و صدای کتاب (`BookNarrationManager`)

- `ChessSoundManager` بر پایه `SoundPool` برای افکت‌های کوتاه حرکت مهره‌ها کار می‌کند و تنظیمات آن در `soundEnabled` و `soundVolume` ذخیره می‌شود.
- `BookNarrationManager` بر پایه موتور استریم فایل‌های صوتی بلند (`NarrationMediaPlayerEngine` / `MediaPlayer`) کار می‌کند و تنظیمات آن در کلیدهای کاملاً مجزا (`narration_enabled`, `narration_speed`, `narration_auto_turn_page`, `narration_last_section_id`, `narration_last_block_id`, `narration_last_position_ms`) در `UserPreferencesManager` ذخیره می‌شود.
- خاموش کردن صدای مهره‌های شطرنج (`soundEnabled = false`) هیچ تأثیری بر فعال بودن خواندن صوتی کتاب (`narrationEnabled`) ندارد و بالعکس.
- هنگام تغییر دستی بخش، خروج از تب کتاب‌خوان (`setTab`) یا بسته شدن صفحه مطالعه (`onDispose` / `onCleared`), خواندن صوتی کتاب به‌طور ایمن متوقف و منابع صوتی آزاد می‌شوند.

---

## ۹. راهنمای اضافه‌کردن فایل‌های صوتی واقعی توسط کاربر در مراحل بعد

برای افزودن فایل‌های صوتی ضبط‌شده فصل اول در آینده بدون نیاز به تغییر کدهای کاتلین:
1. فایل‌های صوتی (با فرمت `.mp3`, `.m4a`, `.ogg` یا `.wav`) را در مسیر زیر قرار دهید:
   `app/src/main/assets/narration/ch01/<section_folder>/<blockId>.mp3`
2. در فایل `app/src/main/assets/narration/manifest.json`، در بخش مربوطه (`sectionId`) یا در فیلد `fullSectionAudioAsset` مسیر نسبی فایل را وارد کنید؛ برای نمونه:
   ```json
   {
     "sectionId": "CH01_SEC_01_OPENING",
     "fullSectionAudioAsset": null,
     "blocks": [
       {
         "blockId": "CH01_SEC_01_H01",
         "audioAsset": "narration/ch01/sec01/CH01_SEC_01_H01.mp3",
         "durationMs": 4200
       },
       {
         "blockId": "CH01_SEC_01_P01",
         "audioAsset": "narration/ch01/sec01/CH01_SEC_01_P01.mp3",
         "durationMs": 18500
       }
     ]
   }
   ```
3. به محض قرارگیری فایل‌ها در `assets/narration/` و ثبت در `manifest.json`، دکمه‌های «پخش صفحه» و «پخش فصل» در `ReaderNarrationBar` به‌طور خودکار فایل‌ها را به ترتیب پخش کرده، بلوک متنی فعال را هایلایت می‌کنند و در حالت پخش فصل، صفحات را همگام با روایت ورق می‌زنند.

---

## ۱۰. گزارش اجرای تست‌های خودکار (۱۱۶ تست — بدون خطا)

فرمان اجراشده:
```bash
gradle :app:testDebugUnitTest --rerun-tasks --console=plain
```

- **تعداد کل تست‌ها:** `116` تست (`0 failures`, `0 errors`, `0 skipped`)
  - `com.example.ExampleRobolectricTest`: **60 تست موفق** (شامل تست‌های جدید نسخه ۱۶: `v16 test A`, `v16 test B`, `v16 test C`, `v16 test D`, `v16 test E1`, `v16 test E2` و تمامی تست‌های نسخه‌های قبل)
  - `com.example.ReaderPaginationRobolectricTest`: **32 تست موفق** (تمامی تست‌های صفحه‌بندی، جهت ورق‌زدن، اندازه‌گیری تخته و صفحات آغازین)
  - `com.example.ChessChapterOneTest`: **23 تست موفق** (تست‌های موتور شطرنج، PGN و FEN)
  - `com.example.ExampleUnitTest`: **1 تست موفق**
- فایل‌های گزارش XML واقعی اجرای تست‌ها در پوشه `test-reports/` به‌روزرسانی شده‌اند:
  - `test-reports/TEST-com.example.ExampleRobolectricTest.xml` (`tests="60" failures="0" errors="0"`)
  - `test-reports/TEST-com.example.ReaderPaginationRobolectricTest.xml` (`tests="32" failures="0" errors="0"`)
  - `test-reports/TEST-com.example.ChessChapterOneTest.xml` (`tests="23" failures="0" errors="0"`)
  - `test-reports/TEST-com.example.ExampleUnitTest.xml` (`tests="1" failures="0" errors="0"`)

---

## ۱۱. فهرست اسکرین‌شات‌های تولیدشده در پوشه `screenshots/`

علاوه بر اسکرین‌شات‌های پایه نسخه‌های قبل، اسکرین‌شات‌های اختصاصی نسخه ۱۶ از رندر واقعی کامپوننت‌ها در حین اجرای تست‌های Robolectric تولید و در پوشه `screenshots/` ذخیره شده‌اند:

1. `screenshots/16_persistent_move_description_after_trail_fade.png` — نمایش پایدار توضیح آخرین حرکت در نوار ثابت پایین تخته پس از محو شدن کامل فلش و شبح Move Trail.
2. `screenshots/17_capture_pulse_radial_mid_animation.png` — نمایش هم‌زمان شبح مهره هم‌رنگ با فلش (`#438F8A`), نبود هرگونه کادر روی خانه‌های مبدأ و مقصد، و پالس دایره‌ای قرمز گرادیانی (`#D9383A`) روی خانه مهره زده‌شده در لحظه میانی انیمیشن Capture.
3. `screenshots/18_bd032_board_stability_wrong_move.png` — ثبات کامل ابعاد تخته شطرنج در تمرین تعاملی `BD-032` هنگام نمایش هم‌زمان توضیح آخرین حرکت، پیام بازخورد حرکت قانونی اشتباه و دکمه «تلاش دوباره».
4. `screenshots/19_reader_narration_bar_audio_not_ready.png` — نمایش نوار کنترل خواندن صوتی کتاب (`ReaderNarrationBar`) در بالای صفحه مطالعه و نمایش محترمانه پیام «فایل صوتی این صفحه هنوز آماده نشده است» در نبود فایل‌های صوتی.

---

## ۱۲. مشخصات بسته تحویلی نهایی

- **نام فایل:** `CH01_CHESS_ANDROID_PROTOTYPE-16.zip`
- **SHA-256:** `47a3e1ab5ecd966a4fd94ca62b84b19f29d4eaed06a7ae3037e1bb1e5c132e86`
- **محتویات:** سورس کامل پروژه اندروید، مانیفست صوتی `app/src/main/assets/narration/manifest.json`، گزارش‌های XML واقعی تست‌ها در `test-reports/`، اسکرین‌شات‌های ۱ تا ۲۰ در `screenshots/` و این گزارش فنی (`IMPLEMENTATION_REPORT.md`)، بدون دایرکتوری‌های `build/`, `.gradle/`, `.idea/` و `local.properties`.
