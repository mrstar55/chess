# گزارش معماری محتوامحور (نسخه ۱۹) — تکمیل جداسازی موتور اپلیکیشن از محتوا، عمومی‌سازی تمرین و روایت صوتی

## ۱. خلاصه اجرایی

در نسخه ۱۹ پروژه (`CH01_CHESS_ANDROID_PROTOTYPE-19.zip`)، جداسازی معماری آغازشده در نسخه ۱۸ به طور کامل در سطح کد سورس نهایی شد تا هیچ وابستگی اجرایی به شناسه‌های اختصاصی فصل اول (`CH01`) یا تمرین `BD-032` در موتور مرکزی اپلیکیشن باقی نماند:

1. **موتور اپلیکیشن (`Application Engine`):**
   - مدل‌های مستقل تمرین (`ExerciseUiState` و `ExercisePhase`) در پکیج `com.example.model` تعریف شده‌اند (نه `typealias` به مدل اختصاصی `Bd032UiState`).
   - موتور تمرین (`ExerciseEngine.kt`) هیچ ایمپورتی از پکیج `com.example.ui` ندارد و هیچ مقایسه رشته‌ای با `"BD-032"` یا `"CH01"` انجام نمی‌دهد.
   - کامپوننت رابط کاربری تمرین به `InteractiveExerciseView` عمومی تبدیل شده که عنوان، صورت سؤال، پیام‌های بازخورد و برچسب‌های تست (`testTag`) را بر اساس `exerciseId` و `ExerciseDefinition` مدیریت می‌کند.
   - مدیر روایت صوتی کتاب (`BookNarrationManager.kt`) هیچ مسیر پیش‌فرض هاردکدشده‌ای برای `CH01` ندارد، در صورت نبود مانیفست فصل جدید به مانیفست فصل اول بازنمی‌گردد، پیام‌های وضعیت را به‌صورت عمومی برای هر فصل نمایش می‌دهد و موقعیت ادامه پخش صوتی را به تفکیک `(bookId, chapterId)` ذخیره می‌کند.
   - انتخاب کتاب و فصل اولیه در `ChapterRepository` و `ChapterViewModel` به‌طور پویا از `LibraryCatalog` انجام می‌شود و عنوان فصل در `ReaderScreen` و `SectionsListScreen` مستقیماً از مانیفست فصل خوانده می‌شود.
2. **بسته‌های محتوای کتاب (`Book Content Packs`):**
   - تمام داده‌های کتاب، فصل‌ها، بخش‌ها، متون، نمودارهای FEN، دنباله‌های PGN، تمرین‌های تعاملی (`exercises.json` همراه با متادیتای کامل فارسی) و مانیفست‌های روایت صوتی در مسیر استاندارد `app/src/main/assets/books/` قرار دارند.

---

## ۲. جدول تکمیل حذف وابستگی‌های باقی‌مانده در نسخه ۱۹

| ردیف | بخش / فایل | وضعیت در نسخه ۱۸ | اصلاح قطعی در نسخه ۱۹ |
|---|---|---|---|
| ۱ | `ChapterModels.kt` و `ChapterViewModel.kt` | `ExerciseUiState` یک `typealias` برای `Bd032UiState` بود و `bd032State` و `bd032InitialFen` در مسیر فعال `ChapterUiState` حضور داشتند. | تعریف کلاس‌های واقعی `ExerciseUiState` و `ExercisePhase` در `com.example.model`؛ حذف فیلد ذخیره‌شده `bd032State`، حذف `bd032InitialFen` و حذف تمام fallbackهای `BD-032` از مسیر اجرایی `ChapterViewModel`. |
| ۲ | `ExerciseEngine.kt` | ایمپورت `com.example.ui.Bd032UiState` و بررسی مستقیم `diagramId == "BD-032"` برای تولید `testTag`. | حذف کامل ایمپورت‌های `com.example.ui` از پکیج `com.example.chess`؛ تولید `tagPrefix` بر اساس `definition.exerciseId.lowercase()` و خواندن پیام‌های بازخورد از `ExerciseDefinition`. |
| ۳ | `ReaderScreen.kt` و `Bd032ExerciseView.kt` | سه شرط fallback به `"BD-032"` و `state.bd032State` در `ReaderScreen.kt` و نام‌گذاری ثابت `bd032_legal_hint_btn` / `bd032_retry_btn`. | حذف کامل شرط‌های `"BD-032"` و `state.bd032State` از `ReaderScreen.kt`؛ تبدیل نمای تمرین به `InteractiveExerciseView` با تگ‌های مبتنی بر `exerciseId` (`exercise_legal_hint_btn_<exerciseId>` و `exercise_retry_btn_<exerciseId>`). |
| ۴ | `ChapterModels.kt` (`defaultExerciseIdForDiagram`) | پیشوند پیش‌فرض `"EX_CH01_"` برای تولید شناسه تمرین. | تغییر به `"EX_${normalized}"` بدون پیشوند `"CH01"` (با حفظ شناسه صریح `"EX_CH01_BD032"` در فایل `CH01/exercises.json`). |
| ۵ | `BookNarrationManager.kt` | مقدار اولیه `activeChapterId = "CH01"`، ثابت `DEFAULT_CH01_MANIFEST_ASSET_PATH`، بازگشت به مانیفست فصل اول در صورت خطا و پیام‌های «فصل اول». | حذف کامل ثابت‌ها و fallbackهای `CH01`، عمومی‌سازی پیام‌های وضعیت («فایل صوتی این فصل هنوز آماده نشده است» و «پخش صوتی این فصل به پایان رسید») و جداسازی ذخیره موقعیت ادامه پخش به تفکیک هر فصل. |
| ۶ | `ReaderScreen.kt` و `SectionsListScreen.kt` | بررسی `if (state.activeChapterId == "CH01") "فصل اول"` در کد رابط کاربری. | استخراج عنوان و برچسب کوتاه فصل مستقیماً از متادیتای بارگذاری‌شده فصل (`manifest.titleFa` / `activeChapterShortTitleFa`) بدون شرط روی `"CH01"`. |

---

## ۳. ساختار نهایی پوشه‌ها و فایل‌های محتوا

```text
app/src/main/assets/books/
├── library.json
└── how_to_win_at_chess/
    └── CH01/
        ├── chapter.json
        ├── diagrams.json
        ├── pgn_sequences.json
        ├── exercises.json
        ├── sections/
        │   ├── 01_opening.json
        │   ├── 02_checkmate.json
        │   ├── 03_resignation.json
        │   ├── 04_timing_out.json
        │   ├── 05_abandonment.json
        │   └── 06_summary.json
        └── narration/
            └── manifest.json
```

---

## ۴. مکانیسم اعتبارسنجی خودکار (`validateContentPacks`)

تسک Gradle اختصاصی `validateContentPacks` در `app/build.gradle.kts` تعریف و به `preBuild` متصل است و علاوه بر بررسی JSON، یکتایی شناسه‌ها، وجود فایل‌ها و اعتبار FEN، در نسخه ۱۹ تطابق `initialFen` تمرین در `exercises.json` با `initialFen` نمودار مرجع در `diagrams.json` نیز در `ContentBlockValidator` راستی‌آزمایی می‌شود.
