# نقشه کامل مهاجرت محتوای فصل اول (`CH01`) — نسخه ۱۸

این سند تناظر یک‌به‌یک تمام داده‌های محتوایی فصل اول را که از کدهای Kotlin نسخه ۱۷.۱ به بسته محتوای استاندارد در `app/src/main/assets/books/how_to_win_at_chess/CH01/` منتقل شده‌اند ثبت می‌کند.

---

## ۱. جدول تناظر بخش‌های شش‌گانه فصل اول

| ترتیب | شناسه بخش (`sectionId`) | نوع بخش (`sectionKind`) | عنوان فارسی (`titleFa`) | صفحات PDF | مسیر قدیمی (نسخه ۱۷.۱) | مسیر جدید در بسته محتوا (نسخه ۱۸) |
|---|---|---|---|---|---|---|
| ۱ | `SEC_CH01_01_OPENING` | `OPENING` | شروع فصل | `[40]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/01_opening.json` |
| ۲ | `SEC_CH01_02_CHECKMATE` | `CONTENT` | مات | `[41, 42, 43, 44, 45]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/02_checkmate.json` |
| ۳ | `SEC_CH01_03_RESIGNATION` | `CONTENT` | تسلیم | `[45, 46]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/03_resignation.json` |
| ۴ | `SEC_CH01_04_TIMING_OUT` | `CONTENT` | اتمام زمان | `[47, 48]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/04_timing_out.json` |
| ۵ | `SEC_CH01_05_ABANDONMENT` | `CONTENT` | ترک بازی | `[48, 49]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/05_abandonment.json` |
| ۶ | `SEC_CH01_06_SUMMARY` | `SUMMARY` | خلاصه | `[50]` | هاردکد در `ChapterRepository.kt` | `books/how_to_win_at_chess/CH01/sections/06_summary.json` |

---

## ۲. جدول تناظر بلوک‌های محتوایی (`orderedContentBlocks`)

| بخش | شناسه بلوک (`blockId`) | نوع بلوک (`type`) | ارجاع (`diagramId` / `exerciseId`) | فایل مقصد در نسخه ۱۸ |
|---|---|---|---|---|
| بخش ۱ | `SEC_CH01_01_OPENING_P001` | `paragraph` | — | `sections/01_opening.json` |
| بخش ۱ | `SEC_CH01_01_OPENING_D025` | `diagram` | `BD-025` | `sections/01_opening.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_H001` | `heading` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_P001` | `paragraph` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_D026` | `diagram` | `BD-026` | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_P002` | `paragraph` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_D027` | `diagram` | `BD-027` | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_P003` | `paragraph` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_D028` | `diagram` | `BD-028` | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_P004` | `paragraph` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_D029` | `diagram` | `BD-029` | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_P005` | `paragraph` | — | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_D030` | `diagram` | `BD-030` | `sections/02_checkmate.json` |
| بخش ۲ | `SEC_CH01_02_CHECKMATE_FN001` | `footnote` | — | `sections/02_checkmate.json` |
| بخش ۳ | `SEC_CH01_03_RESIGNATION_H001` | `heading` | — | `sections/03_resignation.json` |
| بخش ۳ | `SEC_CH01_03_RESIGNATION_P001` | `paragraph` | — | `sections/03_resignation.json` |
| بخش ۳ | `SEC_CH01_03_RESIGNATION_D031` | `diagram` | `BD-031` | `sections/03_resignation.json` |
| بخش ۴ | `SEC_CH01_04_TIMING_OUT_H001` | `heading` | — | `sections/04_timing_out.json` |
| بخش ۴ | `SEC_CH01_04_TIMING_OUT_P001` | `paragraph` | — | `sections/04_timing_out.json` |
| بخش ۴ | `SEC_CH01_04_TIMING_OUT_E032` | `exercise` | `BD-032` / `EX_CH01_BD032` | `sections/04_timing_out.json` |
| بخش ۴ | `SEC_CH01_04_TIMING_OUT_P002` | `paragraph` | — | `sections/04_timing_out.json` |
| بخش ۴ | `SEC_CH01_04_TIMING_OUT_D033` | `diagram` | `BD-033` | `sections/04_timing_out.json` |
| بخش ۵ | `SEC_CH01_05_ABANDONMENT_H001` | `heading` | — | `sections/05_abandonment.json` |
| بخش ۵ | `SEC_CH01_05_ABANDONMENT_P001` | `paragraph` | — | `sections/05_abandonment.json` |
| بخش ۵ | `SEC_CH01_05_ABANDONMENT_D034` | `diagram` | `BD-034` | `sections/05_abandonment.json` |
| بخش ۶ | `SEC_CH01_06_SUMMARY_H001` | `heading` | — | `sections/06_summary.json` |
| بخش ۶ | `SEC_CH01_06_SUMMARY_P001` | `paragraph` | — | `sections/06_summary.json` |
| بخش ۶ | `SEC_CH01_06_SUMMARY_REF001` | `externalReference` | — | `sections/06_summary.json` |

---

## ۳. جدول تناظر نمودارها، دنباله‌های PGN، تمرین و مانیفست صوتی

| نوع داده | مسیر قبلی | مسیر جدید در بسته محتوای `CH01` | توضیحات |
|---|---|---|---|
| کاتالوگ کتابخانه | وجود نداشت | `app/src/main/assets/books/library.json` | معرفی کتاب `how_to_win_at_chess` و فهرست فصل‌ها (فقط `CH01` فعال در نسخه تولیدی) |
| مانیفست فصل اول | وجود نداشت | `app/src/main/assets/books/how_to_win_at_chess/CH01/chapter.json` | معرفی متادیتای فصل اول و ۶ بخش آن |
| ۱۰ نمودار (`BD-025` تا `BD-034`) | `assets/ch01_diagrams.json` + شرط هاردکد `BD-027` در `ChapterRepository.kt` | `app/src/main/assets/books/how_to_win_at_chess/CH01/diagrams.json` | شامل فیلدهای `audit_status` و `audit_warning_fa` برای `BD-027` داخل JSON |
| دنباله‌های PGN | `assets/ch01_pgn_sequences.json` | `app/src/main/assets/books/how_to_win_at_chess/CH01/pgn_sequences.json` | تمام دنباله‌های حرکت فصل اول و راه‌حل‌های تمرین |
| تمرین تعاملی `BD-032` | هاردکد در `ChapterViewModel.kt` و `ReaderScreen.kt` | `app/src/main/assets/books/how_to_win_at_chess/CH01/exercises.json` | تعریف تمرین `EX_CH01_BD032` از نوع `MATE_IN_ONE` با دو راه‌حل `BD032_MATE_QH8` و `BD032_MATE_QH2` |
| مانیفست روایت صوتی (۱۲ قطعه) | `assets/narration/ch01_narration_manifest.json` | `app/src/main/assets/books/how_to_win_at_chess/CH01/narration/manifest.json` | حفظ دقیق ۱۲ قطعه `NAR_CH01_S01_P001` تا `NAR_CH01_S06_REF001` با `audioAvailable: false` |
