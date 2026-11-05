# ممیزی تطبیق منبع — فصل اول، PDF صفحات ۴۰ تا ۵۰

- PDF: `how-to-win-at-chess-by-levy-rozman-NB-003957.pdf`
- SHA-256: `461b30a603b6b1477ca36d8072eb8e463c4c23816686b34717bf8b6af1982ec0`
- بازه: ۴۰ تا ۵۰، هر دو شامل
- نمودارها: BD-025 تا BD-034
- وضعیت ترجمه: پیش‌نویس کامل برای ویراستاری

| PDF | بخش(ها) | بلوک‌ها | نمودار | اشیای تصویری |
|---:|---|---:|---|---:|
| 40 | SEC_CH01_01_OPENING, SEC_CH01_02_CHECKMATE | 8 | BD-025 | 2 |
| 41 | SEC_CH01_02_CHECKMATE | 5 | BD-026 | 3 |
| 42 | SEC_CH01_02_CHECKMATE | 3 | BD-027 | 1 |
| 43 | SEC_CH01_02_CHECKMATE | 3 | BD-028 | 2 |
| 44 | SEC_CH01_02_CHECKMATE | 2 | BD-029 | 2 |
| 45 | SEC_CH01_02_CHECKMATE, SEC_CH01_03_RESIGNATION | 7 | BD-030 | 2 |
| 46 | SEC_CH01_03_RESIGNATION, SEC_CH01_04_TIMING_OUT | 6 | BD-031 | 3 |
| 47 | SEC_CH01_04_TIMING_OUT | 6 | BD-032 | 2 |
| 48 | SEC_CH01_04_TIMING_OUT, SEC_CH01_05_ABANDONMENT | 4 | BD-033 | 3 |
| 49 | SEC_CH01_05_ABANDONMENT | 3 | BD-034 | 1 |
| 50 | SEC_CH01_06_SUMMARY | 9 | — | 0 |

## کنترل‌های ویژه

- صفحه ۴۵ در دو Section مشترک است: پایان Checkmate و آغاز Resignation.
- صفحه ۴۶ در دو Section مشترک است: پایان Resignation و Heading آغاز Timing Out.
- صفحه ۴۸ در دو Section مشترک است: پایان Timing Out و Heading آغاز Abandonment.
- صفحه ۵۰ شامل خلاصه، پیوند Chessly، عبارت Skip Notes و پاورقی پاسخ BD-032 است.
- BD-027 وضعیت `BLOCKED_NEEDS_HUMAN_REVIEW` را حفظ می‌کند.
- تمام image placements استخراج‌شده از صفحات ۴۰ تا ۵۰ در `source_page_index.json` و CSV ثبت شده‌اند. فایل‌های تصویری با بایت‌های خروجی PyMuPDF استخراج شده‌اند؛ بایت فایل‌ها بدون تبدیل فرمت/تغییر اندازه حفظ شده‌اند.
- نمودارهای اصلی در UI باید با FEN موجود در diagrams.json به تخته تعاملی تبدیل شوند؛ فایل‌های تصویری اصلی در بسته منبع برای حفظ مرجع تصویری و کنترل تطبیق نگهداری می‌شوند. از نمایش هم‌زمان تصویر ثابت و تخته تعاملی به‌عنوان دو نمودار تکراری خودداری شود.