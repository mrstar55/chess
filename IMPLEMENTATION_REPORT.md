# گزارش فنی پیاده‌سازی نسخه ۲۰ (`CH01_CHESS_ANDROID_PROTOTYPE-20.zip`)

## ۱. مشخصات بسته‌های ورودی و خروجی

| ویژگی | مقدار |
|---|---|
| **فایل سورس مبنا** | `CH01_CHESS_ANDROID_PROTOTYPE-19.3.zip` |
| **SHA-256 سورس مبنا** | `6048fe882cf32dbbcf12eca2cf050db79c5abc1fee8c1e3c02b336719d9c29db` |
| **حجم سورس مبنا** | `4,257,075` بایت |
| **بسته محتوایی ورودی (Front Matter)** | `FRONT_MATTER_CONTENT_PACK_FA_v0.3.zip` |
| **SHA-256 بسته محتوایی ورودی** | `c50ca90a2b7137cc2652cb3875427f3acba1a1c316e7609e67731564a17e704c` |
| **فایل خروجی نهایی** | `CH01_CHESS_ANDROID_PROTOTYPE-20.zip` |

---

## ۲. خلاصه اقدامات انجام‌شده در نسخه ۲۰ (ورود محتوای مقدماتی صفحات ۱ تا ۳۹ PDF)

در این مرحله، بسته محتوایی تأییدشدهٔ `FRONT_MATTER_CONTENT_PACK_FA_v0.3.zip` (پوشش‌دهندهٔ صفحات فیزیکی ۱ تا ۳۹ فایل PDF اصلی کتاب *How to Win at Chess*) بدون هیچ‌گونه ترجمهٔ مجدد، خلاصه‌سازی، بازنویسی یا تغییر متن، عیناً وارد معماری Content Pack اپلیکیشن شد و به موتور کتاب‌خوان متصل گردید.

### الف) انتقال کامل ۱۲۱ فایل بسته `FRONT_MATTER_CONTENT_PACK_FA_v0.3` به `assets/books/how_to_win_at_chess/`
1. **`front_matter/manifest.json` و `front_matter/table_of_contents.json`:**
   - تعریف ناحیهٔ مستقل `contentRegionId: "FRONT_MATTER"` برای صفحات `1..39` و پیوند به شروع فصل اول (`nextSourcePage: 40`).
2. **`front_matter/pages/page_001.json` تا `page_039.json` (۳۹ فایل صفحه):**
   - صفحات ۱ تا ۱۰: جلد (`COVER`)، تصاویر آغازین (`IMAGE_ONLY`: صفحات ۲ و ۵)، صفحه عنوان (`TITLE_PAGE`: صفحه ۳)، حقوق نشر و عوامل تولید (`COPYRIGHT_AND_CREDITS`: صفحه ۴)، فهرست مطالب (`TABLE_OF_CONTENTS`: صفحات ۶ تا ۹) و تصویر تزئینی (`IMAGE_ONLY`: صفحه ۱۰).
   - صفحات ۱۱ تا ۳۶: مقدمه (`INTRODUCTION`: صفحه ۱۱) و آموزش مبانی شطرنج (`EDUCATIONAL_CONTENT`: صفحات ۱۲ تا ۳۶) شامل معرفی مهره‌ها، قواعد حرکت، آن‌پاسان، قلعه‌رفتن، کیش/کیش‌ومات/پات، نمادگذاری شطرنج، ارزش مهره‌ها و تمرین شطرنج.
   - صفحه ۳۷ (`PART_DIVIDER`): جداکنندهٔ تصویری بخش اول (`0-800 ELO`، شیء `PDFOBJ_067`) همراه با بلوک‌های متنی فارسی مجزا (`بخش اول` و `درجه اِلو: ۰ تا ۸۰۰`) بدون دست‌کاری تصویر اصلی.
   - صفحه ۳۸ (`PART_INTRODUCTION`): صفحهٔ خوش‌آمدگویی به بخش اول همراه با نشان تاج (`PDFOBJ_068`) و بلوک `FM_P038_L001` شامل ۶ هدف یادگیری (با ترجمهٔ دقیق `middlegame` به **«میانه‌بازی»** و عدم وجود کامل واژهٔ ممنوع «وسط‌بازی»).
   - صفحه ۳۹ (`INTERSTITIAL_IMAGE`): صفحهٔ مستقل آغازگر تصویری فصل اول با آیکون رخ در گوشهٔ بالا سمت چپ (`FM_P039_I001` -> `PDFOBJ_069`، `assets/pdf_embedded_objects/pdf_p039_obj069.jpg`). این صفحه حذف یا با صفحهٔ ۴۰ ادغام نشده است.
3. **`assets/pdf_embedded_objects/` (۷۰ فایل شیء تصویری استخراج‌شده از PDF):**
   - حفظ دست‌نخورده و باینری هر ۷۰ فایل (`PDFOBJ_000` تا `PDFOBJ_069`) بدون کراپ، بدون تغییر فرمت و بدون حذف اشیای کوچک یا ماسک‌ها، منطبق با `source_metadata/source_image_manifest.json` و `source_metadata/source_image_placements_pages_001_039.csv`.
4. **`source_links/source_links_pages_001_039.json` و `.csv` (۱۲۶ پیوند):**
   - حفظ و بارگذاری هر ۱۲۶ پیوند صفحات ۱ تا ۳۹، شامل پیوندهای صفحهٔ ۴ (`TenSpeed.com` و پیوندهای صفحات ۲، ۵، ۱۰، ۳۷، ۲۶۱)، پیوندهای فهرست مطالب در صفحات ۶ تا ۹ (از جمله `"CHAPTER ONE"` -> صفحهٔ `39` و `"HOW TO (LITERALLY) WIN AT CHESS"` -> صفحهٔ `40`) و پیوندهای درون‌متنی صفحهٔ ۲۱ به صفحات `52` و `86`.
5. **`terminology/terminology_glossary_fa.csv` و `TRANSLATION_NOTES_FA.md`:**
   - واژه‌نامهٔ رسمی فارسی و یادداشت‌های ترجمه در مسیر `assets/books/how_to_win_at_chess/terminology/`.

---

### ب) پیاده‌سازی قرارداد الزامی `sourcePdfPage` در کتاب‌خوان (`ReaderPaginator` و `ReaderScreen`)
1. **مرجعیت مطلق `sourcePdfPage`:**
   - فیلد `sourcePdfPage` (شمارهٔ ۱-مبنا در PDF اصلی ۵۹۱ صفحه‌ای) به‌عنوان شمارهٔ رسمی صفحه در سراسر مدل داده، صفحه‌بند و هدر کتاب‌خوان (`Modifier.testTag("reader_source_pdf_page_badge")`) به کاربر نمایش داده می‌شود.
2. **پشتیبانی از برش‌های نمایشی موبایل بدون تغییر `sourcePdfPage`:**
   - اگر محتوای یک صفحهٔ فیزیکی PDF در صفحه‌نمایش موبایل به چند برش (`ReaderPage` slice) تقسیم شود، تمام برش‌ها دقیقاً همان `sourcePdfPage` را حفظ می‌کنند و برچسب هدر به‌صورت `صفحه PDF ۱۴ — بخش ۱ از ۶`، `صفحه PDF ۱۴ — بخش ۲ از ۶` و ... نمایش می‌یابد.
3. **عدم ادغام دو `sourcePdfPage` متفاوت:**
   - صفحه‌بند (`ReaderPaginator.paginateFrontMatterPage` و `paginateFrontMatterPages`) هر صفحهٔ منبع را به‌صورت مستقل صفحه‌بندی می‌کند و هرگز بلوک‌های دو `sourcePdfPage` متفاوت را در یک صفحهٔ نمایشی ادغام نمی‌کند.
4. **پایداری در برابر تغییر اندازهٔ فونت و ابعاد صفحه:**
   - تغییر اندازهٔ فونت (`SMALL`, `NORMAL`, `LARGE`) در `ChapterViewModel.setFontScale` همیشه کاربر را روی همان `sourcePdfPage` نگه می‌دارد.

---

### ج) یکپارچه‌سازی معماری ناحیهٔ `FRONT_MATTER` بدون ایجاد فصل جعلی
1. **ثبت `frontMatter` در `books/library.json` و `content_schemas/library.schema.json`:**
   - ناحیهٔ `FRONT_MATTER` به‌عنوان یک بخش مستقل پیش از فصل‌ها با عنوان ناوبری **«پیش‌درآمد و مبانی شطرنج»** و عنوان کامل **«پیش‌درآمد، مقدمه و آموزش مبانی شطرنج»** ثبت شده است.
   - هیچ فصل جعلی مانند `CH00` ساخته نشده و تعداد فصل‌های کتاب تغییر نکرده است.
2. **پشتیبانی از ۴ نوع بلوک محتوایی جدید در موتور کتاب‌خوان:**
   - `HeadingBlock` (با پشتیبانی از فیلدهای تعاملی فهرست مطالب `tocTargetSourcePdfPage`, `tocEntryKind`, `chapterId`)
   - `SourceImageBlock` (رندر مستقیم تصویر اصلی از `assets/books/how_to_win_at_chess/assets/pdf_embedded_objects/` همراه با برچسب `assetId`، کپشن فارسی و `sourcePdfPage`)
   - `OrderedListBlock` (رندر فهرست‌های شماره‌گذاری‌شدهٔ فارسی با ارقام فارسی، مانند ۶ هدف آموزشی صفحهٔ ۳۸)
   - `TableBlock` (رندر جداول آموزشی، مانند جدول نمادگذاری مهره‌ها در صفحهٔ ۳۱ و جدول ارزش مهره‌ها در صفحهٔ ۳۵)

---

## ۳. نتایج اجرای دستورات اعتبارسنجی، تست و ساخت

1. **`gradle :app:validateContentPacks --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL` (`[validateContentPacks] All book content packs in assets/books/ validated successfully.`)
   - بررسی خودکار `front_matter/manifest.json`، وجود و ترتیب صفحات `1..39`، یکتایی `blockId`ها، برابری `sourcePdfPage`، وجود هر ۷۰ فایل تصویر در `assets/pdf_embedded_objects/`، صحت هر ۱۲۶ پیوند، صحت ۶ آیتم `FM_P038_L001` و وجود «میانه‌بازی» در صفحهٔ ۳۸، و وجود `FM_P039_I001` (`PDFOBJ_069`) در صفحهٔ ۳۹.
2. **`gradle :app:testDebugUnitTest --rerun-tasks --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL` — **۱۴۲ تست اجرا شد، ۰ خطا، ۰ شکست:**
     - `ExampleRobolectricTest`: ۶۰ تست
     - `ReaderPaginationRobolectricTest`: ۳۲ تست
     - `ChessChapterOneTest`: ۲۳ تست
     - `FrontMatterVersion20Test`: ۱۴ تست جامع پذیرش نسخه ۲۰
     - `ContentArchitectureVersion19Test`: ۷ تست
     - `ContentArchitectureVersion18Test`: ۵ تست
     - `ExampleUnitTest`: ۱ تست
3. **`gradle :app:assembleDebug --console=plain`**:
   - وضعیت: `BUILD SUCCESSFUL`

---

## ۴. فهرست ۸ اسکرین‌شات واقعی تولیدشده از محیط اجرای تست (`screenshots/`)

| ردیف | مسیر فایل اسکرین‌شات | صفحه PDF | محتوای تأییدشده در تصویر |
|---|---|---|---|
| ۱ | `screenshots/pdf_page_011.webp` | صفحه ۱۱ | صفحه شروع مقدمه («مقدمه — سلام، خوش آمدید! من لوی هستم...») با نشان `صفحه PDF ۱۱` |
| ۲ | `screenshots/pdf_page_014.webp` | صفحه ۱۴ | صفحه چیدمان آغازین تخته شطرنج (`PDFOBJ_018`) با نشان `صفحه PDF ۱۴` |
| ۳ | `screenshots/pdf_page_021.webp` | صفحه ۲۱ | صفحه پیشروی دوخانه‌ای پیاده (`PDFOBJ_036`) و دکمه‌های ارجاع به صفحات ۵۲ و ۸۶ PDF |
| ۴ | `screenshots/pdf_page_036.webp` | صفحه ۳۶ | صفحه «تمرین شطرنج» و مطالب تکمیلی یادگیری با نشان `صفحه PDF ۳۶` |
| ۵ | `screenshots/pdf_page_037.webp` | صفحه ۳۷ | صفحه جداکنندهٔ تصویری بخش اول (`PART ONE / 0-800 ELO`، `PDFOBJ_067`) + لایهٔ متنی فارسی مجزا («بخش اول» / «درجه اِلو: ۰ تا ۸۰۰») |
| ۶ | `screenshots/pdf_page_038.webp` | صفحه ۳۸ | صفحه معرفی بخش اول (`PDFOBJ_068`، «به بخش اول خوش آمدید!») و فهرست ۶ هدف آموزشی شامل «ترکیب‌های کیش‌ومات در میانه‌بازی؛» |
| ۷ | `screenshots/pdf_page_039.webp` | صفحه ۳۹ | صفحه مستقل آغازگر تصویری فصل اول با آیکون رخ (`PDFOBJ_069`) و نشان `صفحه PDF ۳۹` |
| ۸ | `screenshots/pdf_page_040.webp` | صفحه ۴۰ | صفحه شروع فصل اول (`CH01`، بخش ۱: «شروع فصل»، دیاگرام `BD-025`) با نشان `صفحه PDF ۴۰` |
