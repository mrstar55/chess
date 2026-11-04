# راهنمای کامل افزودن محتوا و فصل جدید (نسخه ۱۸)

این راهنما به شما نشان می‌دهد چگونه در **Android Studio** بدون تغییر هیچ فایل Kotlin، متن فارسی، نمودار، تمرین، صوت یا فصل جدیدی به کتاب اضافه کنید.

---

## ۱. افزودن متن ترجمه فارسی به فصل اول (`CH01`)

فایل‌های بخش‌های فصل اول در این مسیر قرار دارند:
`app/src/main/assets/books/how_to_win_at_chess/CH01/sections/`

- `01_opening.json` (شروع فصل)
- `02_checkmate.json` (مات)
- `03_resignation.json` (تسلیم)
- `04_timing_out.json` (اتمام زمان)
- `05_abandonment.json` (ترک بازی)
- `06_summary.json` (خلاصه)

### مراحل درج ترجمه فارسی:
1. فایل بخش موردنظر (مثلاً `02_checkmate.json`) را باز کنید.
2. در آرایه `orderedContentBlocks`، بلوک متنی موردنظر (`"type": "paragraph"` یا `"type": "heading"`) را پیدا کنید.
3. مقدار فیلد `"textFa"` را از `null` به متن فارسی ترجمه‌شده تغییر دهید:
   ```json
   {
     "type": "paragraph",
     "blockId": "SEC_CH01_02_CHECKMATE_P001",
     "textFa": "مات هدف نهایی بازی شطرنج است؛ وضعیتی که در آن شاه در معرض کیش قرار دارد و هیچ راه قانونی برای فرار، گرفتن مهره مهاجم یا بستن مسیر کیش وجود ندارد.",
     "showPlaceholderWhenEmpty": true
   }
   ```
4. در صورت نیاز به افزودن پاراگراف جدید در میان بخش، یک شیء جدید با `blockId` یکتا در محل دقیق خود داخل `orderedContentBlocks` قرار دهید.
5. اگر تمام متون بخش ترجمه شده‌اند، می‌توانید `"translationStatus"` را به `"COMPLETE"` تغییر دهید.
6. صفحه‌بند تطبیقی (`ReaderPaginator`) به‌صورت خودکار متن جدید را اندازه‌گیری کرده و صفحات را بدون سرریز عمودی صفحه‌بندی می‌کند.

---

## ۲. افزودن فصل جدید (مثلاً `CH02`) بدون تغییر کد Kotlin

### گام ۱: کپی کردن قالب آماده
1. محتویات پوشه `starter_pack/content_template/` را کپی کنید.
2. پوشه جدیدی در مسیر زیر بسازید:
   `app/src/main/assets/books/how_to_win_at_chess/CH02/`
3. فایل‌های قالب را داخل پوشه `CH02` قرار دهید:
   - `chapter.json`
   - `diagrams.json`
   - `pgn_sequences.json`
   - `exercises.json`
   - `sections/01_opening.json` (و سایر بخش‌ها)
   - `narration/manifest.json`

### گام ۲: تنظیم `chapter.json`
فایل `app/src/main/assets/books/how_to_win_at_chess/CH02/chapter.json` را باز کنید و مشخصات فصل دوم و فهرست بخش‌های آن را وارد کنید:
```json
{
  "schemaVersion": 1,
  "bookId": "how_to_win_at_chess",
  "chapterId": "CH02",
  "chapterNumber": 2,
  "titleEn": "Chapter 2 Title",
  "titleFa": "عنوان فارسی فصل دوم",
  "diagramsFile": "diagrams.json",
  "pgnSequencesFile": "pgn_sequences.json",
  "exercisesFile": "exercises.json",
  "narrationManifestFile": "narration/manifest.json",
  "sections": [
    {
      "sectionId": "SEC_CH02_01_OPENING",
      "order": 1,
      "sectionKind": "OPENING",
      "titleEn": "Opening",
      "titleFa": "شروع فصل دوم",
      "pdfPages": [51, 52],
      "file": "sections/01_opening.json"
    }
  ]
}
```
- **نکته مهم:** برای بخش پایانی فصل، مقدار `"sectionKind": "SUMMARY"` قرار دهید تا نشان معنایی خلاصه به‌صورت خودکار در کتاب‌خوان نمایش داده شود.

### گام ۳: ثبت فصل جدید در `library.json`
فایل `app/src/main/assets/books/library.json` را باز کنید و ورودی فصل دوم را به آرایه `chapters` اضافه کنید:
```json
{
  "chapterId": "CH02",
  "chapterNumber": 2,
  "titleEn": "Chapter 2 Title",
  "titleFa": "عنوان فارسی فصل دوم",
  "manifestPath": "books/how_to_win_at_chess/CH02/chapter.json",
  "enabled": true
}
```
- به محض اینکه بیش از یک فصل با `"enabled": true` در `library.json` وجود داشته باشد، اپلیکیشن به‌صورت خودکار صفحه **فهرست فصل‌های کتاب** (`ChaptersListScreen`) را پس از صفحه معرفی نمایش می‌دهد.

---

## ۳. افزودن نمودار شطرنج (`diagrams.json`) و دنباله حرکت (`pgn_sequences.json`)

1. در فایل `diagrams.json` فصل موردنظر، نمودار جدید را با `diagram_id` یکتا و رشته `fen` معتبر ۶ بخشی تعریف کنید.
2. اگر نمودار ثابت است، `"pgn_ref": null` بگذارید.
3. اگر نمودار دارای دنباله حرکت قابل پخش است:
   - شناسه دنباله را در `"pgn_ref"` بنویسید (مثلاً `"CH02_LINE_01"`).
   - در فایل `pgn_sequences.json`، شیء مربوط به `"CH02_LINE_01"` را با آرایه `"moves"` (حرکات استاندارد SAN) و آرایه `"fens"` (به طول `moves.size + 1`، که خانه اول آن FEN آغازین و خانه‌های بعدی FEN پس از هر حرکت است) اضافه کنید.
4. اگر نموداری در کتاب دارای اشکال چاپی است و باید مسدود شود، کافی است در `diagrams.json` مقدار `"audit_status": "BLOCKED_NEEDS_HUMAN_REVIEW"` و توضیح فارسی را در `"audit_warning_fa"` قرار دهید.

---

## ۴. افزودن تمرین تعاملی (`exercises.json`)

1. ابتدا نمودار وضعیت شروع تمرین را در `diagrams.json` تعریف کنید.
2. راه‌حل (یا راه‌حل‌های) صحیح تمرین را در `pgn_sequences.json` تعریف کنید.
3. در `exercises.json`، تمرین را با فیلدهای زیر ثبت کنید:
   ```json
   {
     "exerciseId": "EX_CH02_BD202",
     "diagramId": "BD-202",
     "exerciseType": "MATE_IN_ONE",
     "initialFen": "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
     "sideToMove": "white",
     "promptFa": "نوبت سفید است: حرکت مات در یک حرکت را بیابید.",
     "acceptedSolutionIds": ["CH02_MATE_RD8"],
     "successFeedbackFa": "آفرین! شاه حریف مات شد.",
     "wrongMoveFeedbackFa": "این حرکت قانونی است، اما مات در یک حرکت نیست.",
     "illegalMoveFeedbackFa": "این حرکت قانونی نیست.",
     "hintFa": "رخ سفید را به عرض هشتم منتقل کنید.",
     "allowLegalMoveHints": true
   }
   ```
4. در فایل بخش (`sections/*.json`)، یک بلوک از نوع `"exercise"` قرار دهید:
   ```json
   {
     "type": "exercise",
     "blockId": "SEC_CH02_01_E202",
     "diagramId": "BD-202",
     "exerciseId": "EX_CH02_BD202"
   }
   ```

---

## ۵. افزودن فایل‌های صوتی روایت کتاب (`narration/manifest.json`)

1. فایل‌های صوتی ضبط‌شده (`.ogg` یا `.mp3` یا `.wav`) را در مسیر `app/src/main/assets/narration/ch01/` (یا پوشه فصل مربوطه) قرار دهید.
2. در فایل `narration/manifest.json` فصل، برای قطعه متناظر با `blockId`:
   - مسیر فایل را در `"audioAssetPath"` وارد کنید.
   - زمان دقیق را بر حسب میلی‌ثانیه در `"durationMs"` بنویسید.
   - مقدار `"audioAvailable"` را از `false` به `true` تغییر دهید.

---

## ۶. دستور اعتبارسنجی پیش از ساخت APK

پس از هر تغییر در فایل‌های محتوا، دستور زیر را اجرا کنید:
```bash
gradle validateContentPacks
```
اگر هرگونه خطای نحوی JSON، شناسه تکراری، FEN نامعتبر یا ارجاع شکسته وجود داشته باشد، این دستور نام دقیق فایل، شناسه شیء و علت خطا را گزارش می‌دهد.
