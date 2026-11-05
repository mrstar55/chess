# گزارش فنی پیاده‌سازی نسخه ۲۱ (`CH01_CHESS_ANDROID_PROTOTYPE-21.zip`)

## ۱. مشخصات بسته‌های ورودی و خروجی

| ویژگی | مقدار |
|---|---|
| **فایل سورس مبنا** | `CH01_CHESS_ANDROID_PROTOTYPE-20.zip` |
| **بسته محتوایی ورودی (فصل اول — صفحات ۴۰ تا ۵۰)** | `CH01_CONTENT_PAGES_040_050_FA_v0.1.zip` (`CH01_Content_Pages_040-050_FA_v0.1_a19186b4.zip`) |
| **SHA-256 فایل PDF مرجع (`591` صفحه)** | `461b30a603b6b1477ca36d8072eb8e463c4c23816686b34717bf8b6af1982ec0` |
| **فایل خروجی نهایی** | `CH01_CHESS_ANDROID_PROTOTYPE-21.zip` |

---

## ۲. خلاصه اقدامات انجام‌شده در نسخه ۲۱ (ورود محتوای واقعی فصل اول — صفحات PDF ۴۰ تا ۵۰)

در این نسخه، بستهٔ محتوایی تأییدشدهٔ `CH01_CONTENT_PAGES_040_050_FA_v0.1.zip` شامل ترجمهٔ فارسی نهایی، نگاشت دقیق بلوک‌به‌صفحه (`source_page_index.json`)، ۲۱ شیء تصویری استخراج‌شده از صفحات ۴۰ تا ۴۹ PDF و جدول‌های متادیتای منبع، بدون هیچ‌گونه تغییر در ترجمه، خلاصه‌سازی یا افزودن متن دلخواه، در سورس نسخهٔ ۲۰ ادغام شد.

### الف) انتقال و اعتبارسنجی فایل‌های محتوایی در `assets/books/how_to_win_at_chess/`
1. **`CH01/chapter.json` و `CH01/source_page_index.json`:**
   - ثبت بازهٔ صفحات فیزیکی فصل اول (`sourcePdfPageStart = 40`, `sourcePdfPageEnd = 50`) و اتصال مرجع رسمی `source_page_index.json`.
   - نگاشت دقیق هر ۱۱ صفحهٔ فیزیکی PDF (`40..50`) به `sectionRefs`، `orderedBlockIds`، `imagePlacements` و دیاگرام‌های متناظر (`BD-025` تا `BD-034`).
2. **`CH01/sections/01_opening.json` تا `06_summary.json`:**
   - جایگزینی کامل هر ۶ فایل بخش با متون فارسی تأییدشدهٔ بستهٔ محتوایی و حفظ دقیق ترتیب `orderedContentBlocks` و `sourcePdfPage` هر بلوک:
     - `01_opening.json` (`SEC_CH01_01_OPENING`): صفحهٔ ۴۰ (`CH01_P040_B001..B003`, `CH01_P040_D025`, `CH01_P040_B004..B005`).
     - `02_checkmate.json` (`SEC_CH01_02_CHECKMATE`): صفحات ۴۰ تا ۴۵ (`CH01_P040_B006..B007` تا `CH01_P045_D030`, `CH01_P045_B001..B003`).
     - `03_resignation.json` (`SEC_CH01_03_RESIGNATION`): صفحات ۴۵ و ۴۶ (`CH01_P045_B004..B006` و `CH01_P046_B001..B004`, `CH01_P046_D031`).
     - `04_timing_out.json` (`SEC_CH01_04_TIMING_OUT`): صفحات ۴۶ تا ۴۸ (`CH01_P046_B005`, `CH01_P047_B001..B005`, `CH01_P047_EX_BD032`, `CH01_P048_D033`, `CH01_P048_B001`).
     - `05_abandonment.json` (`SEC_CH01_05_ABANDONMENT`): صفحات ۴۸ و ۴۹ (`CH01_P048_B002..B003`, `CH01_P049_D034`, `CH01_P049_B001..B002`).
     - `06_summary.json` (`SEC_CH01_06_SUMMARY`): صفحهٔ ۵۰ (`CH01_P050_B001..B002`, چهار مورد خلاصه `CH01_P050_L001..L004`, پیوند درس تکمیلی `CH01_P050_LINK001`, `CH01_P050_B003`, و پاورقی `CH01_P050_FN001` با ارجاع به صفحهٔ ۴۷).
3. **`assets/pdf_embedded_objects/` (۲۱ فایل تصویر صفحات ۴۰ تا ۴۹ در کنار ۷۰ فایل صفحات ۱ تا ۳۹):**
   - افزودن هر ۲۱ شیء تصویری صفحات ۴۰ تا ۴۹ (`pdf_p040_xref890_01.jpeg` تا `pdf_p049_xref882_01.jpeg`) با تطابق ۱۰۰٪ هش SHA-256 با `PACKAGE_MANIFEST.json` و `source_metadata/source_image_placements_pages_040_050.csv`.
   - اتصال خودکار آیکن اصلی «نکته جالب» (`PDFOBJ_P046_X1111_01`, `visualRole = FUN_FACT_ICON`) به بلوک `CH01_P046_B001` در صفحهٔ ۴۶ و رندر آن در `ReaderScreen` با تگ `inline_source_asset_PDFOBJ_P046_X1111_01`.
4. **حفظ دست‌نخوردهٔ `diagrams.json`، `pgn_sequences.json` و `exercises.json` نسخهٔ ۲۰:**
   - حفظ کامل وضعیت ممیزی مسدود `BD-027` (`audit_status = BLOCKED_NEEDS_HUMAN_REVIEW` و هشدار فارسی آن در صفحهٔ ۴۲).
   - حفظ کامل تمرین تعاملی `BD-032` (`CH01_P047_EX_BD032`, `diagramId = BD-032`, `exerciseId = EX_CH01_BD032`) در صفحهٔ ۴۷ با پذیرش هر دو پاسخ `Qh8#` و `Qh2#`، مخفی بودن پیش‌فرض راهنمای حرکات قانونی و ارجاع متقابل پاورقی صفحهٔ ۵۰ (`CH01_P050_FN001` -> صفحهٔ PDF ۴۷).

---

### ب) نگاشت دقیق صفحات مشترک (`40, 45, 46, 48`) و صفحه‌بندی مبتنی بر `sourcePdfPage`
1. **عدم ادغام دو `sourcePdfPage` متفاوت در `ReaderPaginator`:**
   - در `ReaderPaginator.paginateSection`، مرز `sourcePdfPage` به‌صورت قطعی اعمال می‌شود تا هیچ دو بلوکی با `sourcePdfPage` متفاوت (مانند بلوک‌های صفحهٔ ۴۰ و ۴۱ در بخش کیش‌ومات، یا صفحات ۴۵ و ۴۶ در بخش تسلیم، یا صفحات ۴۶، ۴۷ و ۴۸ در بخش اتمام زمان) هرگز در یک صفحه یا برش (`ReaderPage`) قرار نگیرند.
2. **پشتیبانی از صفحات مشترک بین دو بخش (`ReaderPaginator.paginateChapterSourcePdfPage` و `ChapterViewModel.navigateToSourcePdfPage`):**
   - هنگام ناوبری به هر یک از صفحات ۴۰ تا ۵۰ (از فهرست مطالب، نوار انتخاب صفحهٔ PDF فصل اول `chapter_pdf_page_chip_40..50`، یا پیوند پاورقی صفحهٔ ۵۰ به صفحهٔ ۴۷)، `ChapterViewModel.navigateToSourcePdfPage` مستقیماً از `CH01/source_page_index.json` و آرایهٔ `orderedBlockIds` همان صفحه استفاده می‌کند.
   - برای صفحات مشترک (`40`, `45`, `46`, `48`)، تمام بلوک‌های متعلق به همان `sourcePdfPage` از هر دو بخش به ترتیب دقیق PDF نمایش داده می‌شوند، بدون آنکه بلوکی از صفحهٔ قبلی یا بعدی وارد شود.
   - ورق زدن به جلو/عقب (`nextPage` / `prevPage`) در حالت صفحه‌محور، به ترتیب پیوسته بین صفحات فیزیکی ۴۰ تا ۵۰ حرکت می‌کند و در صفحهٔ ۳۹ به پیش‌درآمد (`FRONT_MATTER`) متصل می‌شود.
3. **ذخیره و بازیابی محل مطالعه (`UserPreferencesManager`):**
   - ذخیره و بازیابی هم‌زمان `chapterId + sourcePdfPage + sectionId + blockId + pageId`.

---

## ۳. نتایج اجرای دستورات اعتبارسنجی، تست و ساخت

1. **`gradle :app:validateContentPacks --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL` (`[validateContentPacks] All book content packs in assets/books/ validated successfully.`)
   - بررسی خودکار `front_matter` (صفحات ۱ تا ۳۹ و ۷۰ تصویر) + `CH01` (شامل `chapter.json`، ۶ فایل بخش، `source_page_index.json`، تطابق تمام `orderedBlockIds` و وجود هر ۲۱ فایل تصویر صفحات ۴۰ تا ۴۹).
2. **`gradle :app:testDebugUnitTest --rerun-tasks --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL` — **۱۵۴ تست اجرا شد، ۰ خطا، ۰ شکست:**
     - `Ch01Pages040To050Version21Test`: **۱۲ تست الزامی نسخه ۲۱** (پوشش کامل هر ۱۲ شرط بخش ۱۰ پرامپت)
     - `FrontMatterVersion20Test`: **۱۴ تست**
     - `ExampleRobolectricTest`: **۶۰ تست**
     - `ReaderPaginationRobolectricTest`: **۳۲ تست**
     - `ChessChapterOneTest`: **۲۳ تست**
     - `ContentArchitectureVersion19Test`: **۷ تست**
     - `ContentArchitectureVersion18Test`: **۵ تست**
     - `ExampleUnitTest`: **۱ تست**
3. **`gradle :app:assembleDebug --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL`
