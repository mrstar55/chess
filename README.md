# Interactive Chess Book — Data-Driven Android Learning Engine (Version 21)

- **Official Book Title:** *How to Win at Chess* (591 pages in the original PDF).
- **Front Matter and Chess Fundamentals (`FRONT_MATTER`):** Physical PDF pages 1–39, including the cover, title page, copyright information, table of contents, introduction, complete instruction on chess pieces and game rules, chess notation, piece values, chess exercises, section divider, introduction to Part One (0–800 Elo), and a rook illustration on page 39.
- **Chapter One (`CH01`):** *How to (Literally) Win at Chess* — **Physical PDF pages 40–50**, featuring the complete Persian translation, precise block-to-page mapping (`source_page_index.json`), 21 source image objects, 10 chess diagrams (`BD-025` through `BD-034`), and the interactive exercise `BD-032`.

This project is a native Android implementation built with **Kotlin and Jetpack Compose**, featuring an architecture that completely separates the application engine from book content packs (**Application Engine + Book Content Packs**).

In Version **21**, the approved content pack `CH01_CONTENT_PAGES_040_050_FA_v0.1.zip`, covering PDF pages 40–50 of Chapter One, has been integrated alongside the existing content for pages 1–39 (`FRONT_MATTER`), without modifying, summarizing, or adding any new text to the original approved Persian translation.

---

## 1. Key Features of Version 21

### 1.1 Complete and Faithful Chapter One Content (PDF Pages 40–50)

- `books/how_to_win_at_chess/CH01/chapter.json` and `CH01/source_page_index.json` define the page range from 40 to 50 and provide precise mappings between each physical PDF page and its sections (`sectionRefs`), content blocks (`orderedBlockIds`), source images (`imagePlacements`), and chess diagrams.
- `books/how_to_win_at_chess/CH01/sections/01_opening.json` through `06_summary.json` contain the complete Persian text of all six sections of Chapter One, preserving the approved chess terminology:
  - `کیش‌ومات` (Checkmate)
  - `مات اسکالر` (Scholar's Mate)
  - `مات احمقانه` (Fool's Mate)
  - `تسلیم` (Resignation)
  - `اتمام زمان` (Timeout)
  - `ترک بازی` (Abandonment)
  - `ناکافی بودن مهره برای مات` (Insufficient Mating Material)
  - `میانه‌بازی` (Middlegame)
- `books/how_to_win_at_chess/assets/pdf_embedded_objects/` contains 91 image objects extracted from the PDF:
  - 70 image objects from pages 1–39.
  - 21 image objects from pages 40–49.
- This includes the "Interesting Fact" icon (`PDFOBJ_P046_X1111_01`) on page 46.

### 1.2 Precise Management of Shared Pages (40, 45, 46, 48) and `sourcePdfPage` Authority

- No application page or display crop (`ReaderPage`) ever combines content from two different `sourcePdfPage` values.
- Navigation to pages 40–50 is handled through `source_page_index.json`.
- Shared pages between sections (40, 45, 46, and 48) display all content blocks belonging to the same PDF page, in their exact original source order.

### 1.3 Preservation of Exercise `BD-032` and Audit Status of `BD-027`

- The interactive exercise `CH01_P047_EX_BD032` (`BD-032` / `EX_CH01_BD032`) on page 47 accepts both answers: `Qh8#` and `Qh2#`.
- The legal-move hint is hidden by default.
- The footnote on page 50 (`CH01_P050_FN001`) includes an interactive link back to page 47.
- Diagram `BD-027` on page 42 retains its `BLOCKED_NEEDS_HUMAN_REVIEW` status and its Persian warning message.

---

## 2. Project Validation, Testing, and Build Commands

```bash
# Automatically validate the book content packs
# FRONT_MATTER pages 1–39 + CH01 pages 40–50
gradle :app:validateContentPacks --console=plain

# Run all 154 unit, Robolectric, and Compose UI tests
gradle :app:testDebugUnitTest --rerun-tasks --console=plain

# Build the Debug APK
gradle :app:assembleDebug --console=plain
```

### Test Reports

**154 tests — 0 errors and 0 failures.**

The test suite includes:

- **`Ch01Pages040To050Version21Test` — 12 comprehensive Version 21 tests**

  These tests cover:
  - Availability and navigation of pages 40–50.
  - Page 40 with `CH01_P040_B001..B007` and `BD-025`.
  - Page 42 with the `BD-027` audit warning.
  - Shared page 45.
  - Shared page 46 with the `PDFOBJ_P046_X1111_01` icon and the final section title `اتمام زمان`.
  - Page 47 with the `BD-032` exercise and both accepted answers (`Qh8#` and `Qh2#`).
  - The default hidden state of the `BD-032` legal-move hint.
  - Shared page 48 with `BD-033` and the beginning of the `ترک بازی` section.
  - Page 49 with `BD-034` and the story of Steinitz and von Bardeleben.
  - Page 50 with four summary items, a link to a supplementary lesson, and a footnote linking back to page 47.
  - Availability of all 21 images in the assets.
  - Verification that no application page combines content from two different `sourcePdfPage` values.

- **`FrontMatterVersion20Test` — 14 tests**
- **`ExampleRobolectricTest` — 60 tests**
- **`ReaderPaginationRobolectricTest` — 32 tests**
- **`ChessChapterOneTest` — 23 tests**
- **`ContentArchitectureVersion19Test` — 7 tests**
- **`ContentArchitectureVersion18Test` — 5 tests**
- **`ExampleUnitTest` — 1 test**
