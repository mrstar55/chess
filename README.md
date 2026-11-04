# کتاب تعاملی شطرنج — موتور آموزشی داده‌محور اندروید (نسخه ۲۰)

- **عنوان رسمی کتاب:** *How to Win at Chess*
- **پیش‌درآمد و مبانی شطرنج (`FRONT_MATTER`):** صفحات فیزیکی ۱ تا ۳۹ فایل PDF (جلد، صفحه عنوان، حقوق نشر، فهرست مطالب، مقدمه، آموزش کامل مهره‌ها و قواعد بازی، نمادگذاری شطرنج، ارزش مهره‌ها، تمرین شطرنج، جداکننده و معرفی بخش اول ۰ تا ۸۰۰ اِلو و صفحه تصویری رخ در صفحه ۳۹)
- **فصل اول (`CH01`):** *How to (Literally) Win at Chess* (شروع عنوان فصل از صفحه ۴۰ PDF؛ صفحات ۴۰ تا ۵۰)

این پروژه پیاده‌سازی بومی اندروید (**Kotlin + Jetpack Compose**) با **معماری جداسازی کامل موتور اپلیکیشن از محتوای کتاب (`Application Engine + Book Content Packs`)** در نسخه **۲۰** است. در این نسخه، بستهٔ محتوایی تأییدشدهٔ `FRONT_MATTER_CONTENT_PACK_FA_v0.3.zip` (شامل تمام صفحات فیزیکی ۱ تا ۳۹ PDF، ۷۰ شیء تصویری اصلی، ۱۲۶ پیوند درون‌کتابی و بیرونی، فهرست مطالب تعاملی و واژه‌نامهٔ رسمی فارسی) بدون هیچ‌گونه تغییر یا بازنویسی متنی وارد معماری Content Pack شده و با قرارداد مرجعیت شماره صفحهٔ فیزیکی PDF (`sourcePdfPage`) به کتاب‌خوان متصل گردیده است.

---

## ۱. معماری و ویژگی‌های کلیدی پروژه (نسخه ۲۰)

### الف) معماری محتوامحور (`Application Engine + Book Content Packs`)
1. **ناحیهٔ مستقل پیش‌درآمد و مبانی شطرنج (`contentRegionId = "FRONT_MATTER"`):**
   - `books/library.json`: تعریف ناحیهٔ مستقل `frontMatter` پیش از فهرست فصل‌ها با عنوان ناوبری **«پیش‌درآمد و مبانی شطرنج»** (صفحات PDF ۱ تا ۳۹) بدون ایجاد فصل جعلی `CH00`.
   - `books/how_to_win_at_chess/front_matter/manifest.json`: مانیفست رسمی صفحات ۱ تا ۳۹ و اتصال `nextSourcePage: 40` به شروع فصل اول (`CH01`).
   - `books/how_to_win_at_chess/front_matter/pages/page_001.json` تا `page_039.json`: ۳۹ فایل صفحهٔ مستقل منطبق با صفحات فیزیکی ۱ تا ۳۹ PDF.
   - `books/how_to_win_at_chess/front_matter/table_of_contents.json`: فهرست مطالب تعاملی استخراج‌شده از صفحات ۶ تا ۹ PDF (با هدف‌گیری دقیق صفحهٔ `39` برای `"CHAPTER ONE"` و صفحهٔ `40` برای `"HOW TO (LITERALLY) WIN AT CHESS"`).
   - `books/how_to_win_at_chess/assets/pdf_embedded_objects/`: هر ۷۰ فایل شیء تصویری اصلی استخراج‌شده از PDF (`PDFOBJ_000` تا `PDFOBJ_069`).
   - `books/how_to_win_at_chess/source_links/source_links_pages_001_039.json`: هر ۱۲۶ پیوند اصلی صفحات ۱ تا ۳۹.
   - `books/how_to_win_at_chess/terminology/terminology_glossary_fa.csv`: واژه‌نامهٔ مصوب فارسی (از جمله ترجمهٔ الزامی `middlegame` به **«میانه‌بازی»**).
2. **قرارداد الزامی `sourcePdfPage` در کتاب‌خوان:**
   - نمایش شمارهٔ صفحهٔ فیزیکی ۱-مبنای PDF (`sourcePdfPage`) به‌عنوان مرجع اصلی شماره صفحه در هدر کتاب‌خوان (`reader_source_pdf_page_badge`).
   - حفظ همان `sourcePdfPage` در تمام برش‌های نمایشی یک صفحهٔ بلند روی موبایل (`صفحه PDF ۱۴ — بخش ۱ از ۶` و ...).
   - عدم ادغام محتوای دو `sourcePdfPage` متفاوت در یک صفحهٔ نمایشی و عدم تغییر `sourcePdfPage` هنگام تغییر اندازهٔ فونت.
3. **بستهٔ محتوای فصل اول (`CH01` — صفحات ۴۰ تا ۵۰ PDF):**
   - حفظ کامل هر ۶ بخش فصل اول (`01_opening.json` تا `06_summary.json`)، ۱۰ نمودار (`BD-025` تا `BD-034`)، وضعیت ممیزی مسدود `BD-027`، دنباله‌های PGN، تمرین تعاملی `BD-032` (`EX_CH01_BD032`) و مانیفست صوتی فصل اول.

---

## ۲. دستورات اعتبارسنجی، تست و ساخت پروژه

```bash
# اعتبارسنجی خودکار بسته‌های محتوای کتاب (شامل FRONT_MATTER و CH01)
gradle :app:validateContentPacks --console=plain

# اجرای تمام ۱۴۲ تست واحد، Robolectric و Compose UI
gradle :app:testDebugUnitTest --rerun-tasks --console=plain

# ساخت فایل APK نسخه Debug
gradle :app:assembleDebug --console=plain
```

- **گزارش‌های تست (۱۴۲ تست با ۰ خطا و ۰ شکست):**
  - `ExampleRobolectricTest`: **۶۰ تست** (تست‌های انیمیشن، شبح مهره، مسیر حرکت، پالس قرمز Capture، توضیح پایدار حرکت، ثبات تخته، صدای ۱۱گانه شطرنج و زیرساخت خواندن صوتی کتاب)
  - `ReaderPaginationRobolectricTest`: **۳۲ تست** (تست‌های کتاب‌خوان صفحه‌گردان فارسی، عدم اسکرول عمودی، هندسه مربع خانه‌های تخته، نشان‌های معنایی و صفحات آغازین)
  - `ChessChapterOneTest`: **۲۳ تست** (قوانین شطرنج، Perft، FEN، PGN و یکپارچگی داده‌های فصل اول)
  - `FrontMatterVersion20Test`: **۱۴ تست جامع نسخه ۲۰** (بارگذاری صفحات ۱ تا ۳۹، یکتایی و ثبات `sourcePdfPage`، عدم ادغام صفحات منبع، حفظ `sourcePdfPage` در برش‌های موبایل و تغییر فونت، وجود هر ۷۰ تصویر `PDFOBJ_000..069`، تطابق بلوک‌های `sourceImage`، اعتبار هر ۱۲۶ پیوند، ناوبری فهرست مطالب صفحه ۶ به صفحات ۳۹ و ۴۰، صفحه ۳۸ با ۶ هدف آموزشی و «میانه‌بازی»، حفظ صفحه ۳۹ با آیکون رخ `PDFOBJ_069`، عدم تغییر عملکرد فصل اول و تولید ۸ اسکرین‌شات واقعی صفحات ۱۱، ۱۴، ۲۱، ۳۶، ۳۷، ۳۸، ۳۹ و ۴۰)
  - `ContentArchitectureVersion19Test`: **۷ تست**
  - `ContentArchitectureVersion18Test`: **۵ تست**
  - `ExampleUnitTest`: **۱ تست**
