# گزارش پیاده‌سازی و تکمیل سیستم محتوای ترتیبی و نقشه ترجمه — فصل اول (نسخه ۰۷)

- **عنوان رسمی کتاب:** *How to Win at Chess*
- **عنوان فصل اول:** *How to (Literally) Win at Chess*

این گزارش مستندات فنی کامل نسخه `-07` نمونه اولیه فصل اول را ارائه می‌دهد. در این مرحله، بدون هیچ‌گونه تغییر در `ChessEngine`، `ChessRulesAdapter`، قوانین `FEN` و `PGN`، تمرین `BD-032`، وضعیت `BD-027`، طراحی ظاهری و معماری `MVVM`، اصلاحات دقیق سیستم نمایش پیام آماده‌سازی ترجمه، پشتیبانی کامل همه بلوک‌های متنی از نقشه ترجمه، شفاف‌سازی وضعیت اسکلت اولیه بلوک‌ها و تکمیل بسته سورس Android Studio انجام شد.

---

## ۱. اصلاح سیستم نمایش پیام آماده‌سازی ترجمه در هر بخش (`ChapterModels.kt` و `ReaderScreen.kt`)

### مشکل قبلی در نسخه ۰۶:
در `SectionContentBlockItem` برای هر `ParagraphBlock`، `HeadingBlock` یا `FootnoteBlock` خالی که `showPlaceholderWhenEmpty = true` داشت، یک `PendingTranslationSlotCard` به‌صورت مستقل نمایش داده می‌شد؛ بنابراین اگر یک بخش دارای سه پاراگراف ترجمه‌نشده بود، پیام آماده‌سازی سه بار تکرار می‌شد.

### راه‌حل معماری پیاده‌سازی‌شده در نسخه ۰۷:
- در کلاس `TranslationSlot` متدهای `firstPendingPlaceholderBlockId(): String?` و `shouldShowPlaceholderForBlock(blockId: String): Boolean` تعریف شدند:
  1. اگر وضعیت ترجمه بخش `TranslationStatus.COMPLETE` باشد، `firstPendingPlaceholderBlockId()` مقدار `null` برمی‌گرداند.
  2. در غیر این صورت، فهرست `orderedContentBlocks` به ترتیب پیمایش می‌شود و **تنها شناسه (`blockId`) اولین بلوک متنی ترجمه‌نشده** که `showPlaceholderWhenEmpty == true` دارد برگردانده می‌شود.
  3. در `ReaderScreen.kt` (`SectionContentBlockItem`)، کارت `PendingTranslationSlotCard` فقط زمانی رندر می‌شود که `section.translationSlot.shouldShowPlaceholderForBlock(block.blockId)` برقرار باشد.
- **رفتار تضمین‌شده:**
  - پیام آماده‌سازی («محتوای این بخش در حال آماده‌سازی است.») **حداکثر یک‌بار در هر بخش** و دقیقاً در محل **اولین بلوک متنی ترجمه‌نشده** نمایش داده می‌شود.
  - بلوک‌های متنی خالی بعدی پیام تکراری نمایش نمی‌دهند.
  - بلوک‌هایی که `showPlaceholderWhenEmpty = false` دارند بدون نمایش پیام و بدون تغییر ترتیب سایر بلوک‌ها رندر می‌شوند.
  - به محض تزریق ترجمه برای اولین بلوک متنی، پیام آماده‌سازی به‌طور خودکار به **بلوک متنی خالی بعدی** منتقل می‌شود.
  - پس از ترجمه شدن تمام بلوک‌های متنی بخش، پیام آماده‌سازی کاملاً حذف می‌شود.

---

## ۲. پشتیبانی یکپارچه نقشه ترجمه (`translationsByBlockId`) برای همه بلوک‌های متنی از جمله `ExternalReferenceBlock`

- متد یکپارچه `resolveBlockText(block: SectionContentBlock): String?` در `TranslationSlot` اضافه شد که برای تمامی بلوک‌های متنی زیر، ابتدا مقدار موجود در `translationsByBlockId[block.blockId]` را بررسی کرده و در صورت نبود، به فیلد درون‌خطی بلوک مراجعه می‌کند:
  - `ParagraphBlock` (`textFa`)
  - `HeadingBlock` (`textFa`)
  - `FootnoteBlock` (`textFa`)
  - `ExternalReferenceBlock` (`titleFa`)
- در `ReaderScreen.kt` رندر `ExternalReferenceBlock` اصلاح شد تا عنوان فارسی خود را از `section.translationSlot.resolveBlockText(block)` دریافت کند؛ بنابراین عنوان ارجاع خارجی نیز بدون دستکاری `orderedContentBlocks` از طریق `withBlockTranslations` قابل تزریق و به‌روزرسانی است.

---

## ۳. وضعیت فعلی ترتیب محتوای کتاب (اسکلت اولیه در برابر فهرست نهایی مدیر پروژه)

- چیدمان فعلی `orderedContentBlocks` در `ChapterRepository.kt` (برای نمونه در بخش `Checkmate`: یک بلوک پاراگراف و سپس پنج بلوک نمودار `BD-026` تا `BD-030`) یک **اسکلت اولیه ساختاری** است و **ترتیب نهایی پاراگراف‌به‌پاراگراف کتاب نیست**.
- هیچ پاراگراف یا توضیحی حدس زده نشده و هیچ متن انگلیسی جدیدی اضافه نشده است.
- معماری `orderedContentBlocks` و `translationsByBlockId` کاملاً آماده است تا پس از دریافت فهرست نهایی بلوک‌ها و شناسه‌های آن‌ها از مدیر پروژه، ترتیب دقیق پاراگراف‌ها و نمودارهای صفحات PDF کتاب جایگذاری شود.

---

## ۴. اصلاح بسته سورس کامل Android Studio (رفع مشکل حذف فایل‌های Gradle در ZIP)

- در نسخه قبلی، به دلیل الگوی استثنای `-x "*.gradle*"` در دستور `zip`، فایل‌های `build.gradle.kts`، `settings.gradle.kts`، `app/build.gradle.kts`، `gradle.properties` و پوشه `gradle/` از بسته ZIP حذف شده بودند.
- در نسخه `-07`، دستور بسته‌بندی اصلاح شد تا صرفاً کش‌های ساخت (`.gradle/*`، `build/*`، `app/build/*`) و فایل‌های `.apk` حذف شوند و تمامی فایل‌های پروژه Android Studio در بسته نهایی حضور داشته باشند:
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle.properties`
  - `gradle/libs.versions.toml`
  - `gradle/wrapper/gradle-wrapper.properties` و `gradle/wrapper/gradle-wrapper.jar`
  - `gradlew` و `gradlew.bat`
  - `app/src/main/AndroidManifest.xml`
  - تمامی فایل‌های Kotlin، Assets، Resources، تست‌ها، مستندات، گزارش‌های تست (`test-reports/`) و اسکرین‌شات‌ها (`screenshots/`).

---

## ۵. گزارش اجرای ۴۳ تست خودکار (شامل تست‌های جدید نسخه ۰۷)

تمامی **۴۳ تست خودکار** پروژه با دستور `gradle :app:testDebugUnitTest --rerun-tasks --console=plain` اجرا و با موفقیت کامل (`0 failures, 0 errors`) تأیید شدند:

### فهرست ۱۱ تست معماری محتوای ترتیبی و نقشه ترجمه در `ExampleRobolectricTest.kt`:
1. `test 1 - preserve interleaved block order during rendering`: حفظ ترتیب درهم‌تنیده بلوک‌ها (`P001 -> D026 -> P002 -> D027`) هنگام رندر در Compose UI.
2. `test 2 - render paragraph before diagram`: نمایش پاراگراف متنی پیش از نمودار (`P001` بالاتر از `BD-025`).
3. `test 3 - render paragraph after diagram`: نمایش پاراگراف متنی پس از نمودار (`P002` پایین‌تر از `BD-025`).
4. `test 4 - render multiple diagrams in a section in exact registered order`: نمایش چند نمودار در یک بخش (`BD-026` تا `BD-030`) با ترتیب دقیق ثبت‌شده.
5. `test 5 - detect duplicate blockIds across blocks`: شناسایی شناسه‌های تکراری بلوک‌ها توسط `ContentBlockValidator`.
6. `test 6 - detect DiagramBlock with invalid diagramId`: شناسایی `DiagramBlock` و `ExerciseBlock` با شناسه نمودار نامعتبر (`BD-999` یا خالی).
7. `test 7 - preserve the 6 primary TranslationSlot sectionIds`: حفظ و اعتبارسنجی هر ۶ شناسه اصلی `TranslationSlot`.
8. `test 8 - placeholder notice is not repeated under diagrams`: تأیید نمایش تنها یک‌بار پیام آماده‌سازی در بخش ۲ و عدم تکرار آن زیر نمودارها.
9. `test 9 - empty block does not affect other blocks`: امکان خالی ماندن یک بلوک (`showPlaceholderWhenEmpty = false`) بدون نمایش پیام اضافی و بدون تأثیر بر رندر و ترتیب سایر بلوک‌ها.
10. `test 10 - single placeholder per section with 3 empty paragraphs 2 diagrams and 1 translated paragraph shifts and disappears`: بررسی بخش دارای ۳ پاراگراف خالی، ۲ نمودار و ۱ پاراگراف ترجمه‌شده؛ تأیید نمایش **فقط یک** پیام آماده‌سازی در اولین پاراگراف خالی، حفظ ترتیب بلوک‌ها و نمایش نمودارها، انتقال خودکار پیام آماده‌سازی به پاراگراف خالی دوم پس از تزریق ترجمه برای پاراگراف اول، و حذف کامل پیام آماده‌سازی پس از ترجمه تمام پاراگراف‌ها.
11. `test 11 - ExternalReferenceBlock translation updates via Translation Map without changing orderedContentBlocks`: تأیید تغییر و رندر عنوان `ExternalReferenceBlock` از طریق نقشه ترجمه (`withBlockTranslations`) بدون تغییر در `orderedContentBlocks`.

| کلاس تست | فایل گزارش XML | تعداد `@Test` | وضعیت |
|---|---|---|---|
| `com.example.ChessChapterOneTest` | `TEST-com.example.ChessChapterOneTest.xml` | **23** | **PASS (23/23)** |
| `com.example.ExampleRobolectricTest` | `TEST-com.example.ExampleRobolectricTest.xml` | **19** | **PASS (19/19)** |
| `com.example.ExampleUnitTest` | `TEST-com.example.ExampleUnitTest.xml` | **1** | **PASS (1/1)** |
| **مجموع کل** | — | **43** | **PASS (43/43)** |
