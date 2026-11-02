package com.example.data

import android.content.Context
import com.example.chess.ChessEngine
import com.example.chess.PgnSequence
import com.example.model.AuditStatus
import com.example.model.ContentBlockValidator
import com.example.model.DiagramItem
import com.example.model.SectionContentBlock
import com.example.model.SectionItem
import com.example.model.TranslationSlot
import com.example.model.TranslationStatus
import org.json.JSONObject

class ChapterRepository(private val context: Context) {

    private var cachedDiagrams: List<DiagramItem>? = null
    private var cachedSections: List<SectionItem>? = null
    private var cachedPgnSequences: Map<String, PgnSequence>? = null

    fun getDiagrams(): List<DiagramItem> {
        cachedDiagrams?.let { return it }
        val loaded = loadDiagramsFromAssets()
        cachedDiagrams = loaded
        return loaded
    }

    fun getSections(): List<SectionItem> {
        cachedSections?.let { return it }
        val loaded = loadSectionsFromAssets()
        cachedSections = loaded
        return loaded
    }

    fun getPgnSequence(pgnRef: String): PgnSequence? {
        if (cachedPgnSequences == null) {
            cachedPgnSequences = loadPgnSequencesFromAssets()
        }
        return cachedPgnSequences?.get(pgnRef)
    }

    fun getAllPgnSequences(): Map<String, PgnSequence> {
        if (cachedPgnSequences == null) {
            cachedPgnSequences = loadPgnSequencesFromAssets()
        }
        return cachedPgnSequences ?: emptyMap()
    }

    /**
     * Validates that:
     * 1. Every diagram's pgnRef (when non-null) resolves to a valid PgnSequence in chapter_01_pgn_steps.json
     *    and its precomputed replay steps match the diagram's initialFen at the end.
     * 2. Every diagram's solutionPgnRefs (when non-empty) resolve to valid PgnSequences in chapter_01_pgn_steps.json
     *    and start from the diagram's initialFen.
     * 3. All 6 TranslationSlots, unique content block IDs, and DiagramBlock/ExerciseBlock references are valid.
     */
    fun validateAllReferencesAndReplays(): Boolean {
        val sections = getSections()
        val diagrams = getDiagrams()
        val allSequences = getAllPgnSequences()
        if (sections.size != 6 || diagrams.isEmpty() || allSequences.isEmpty()) return false

        val blockValidation = ContentBlockValidator.validateSectionsAndBlocks(sections, diagrams)
        if (!blockValidation.isValid) return false

        for (diagram in diagrams) {
            // Check single-line PGN replay reference
            val pgnRef = diagram.pgnRef
            if (pgnRef != null) {
                val seq = allSequences[pgnRef] ?: return false
                if (!seq.validateIntegrity(
                        expectedStartFen = ChessEngine.STANDARD_START_FEN,
                        expectedFinalFen = diagram.initialFen
                    )
                ) {
                    return false
                }
            }

            // Check exercise solution PGN references
            for (solRef in diagram.solutionPgnRefs) {
                val solSeq = allSequences[solRef] ?: return false
                if (!solSeq.validateIntegrity(
                        expectedStartFen = diagram.initialFen
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun loadDiagramsFromAssets(): List<DiagramItem> {
        val jsonString = context.assets.open("chapter_01_diagram_data.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)
        val diagramsArray = root.getJSONArray("diagrams")
        val result = mutableListOf<DiagramItem>()

        for (i in 0 until diagramsArray.length()) {
            val obj = diagramsArray.getJSONObject(i)
            val id = obj.getString("diagram_id")
            val page = obj.getInt("pdf_page")
            val section = obj.optString("section", "")
            val bookContext = obj.optString("book_context", "")
            val diagramKind = obj.optString("diagram_kind", "")
            val interaction = obj.optString("recommended_interaction", "")
            val fen = obj.getString("fen")
            val sideToMove = obj.optString("side_to_move", "white")
            val moveHistory = obj.optString("move_history", "")
            val pgnRef = if (obj.isNull("pgn_ref")) null else obj.optString("pgn_ref")

            val solutionRefs = mutableListOf<String>()
            val solutionRefsArray = obj.optJSONArray("solution_pgn_refs")
            if (solutionRefsArray != null) {
                for (s in 0 until solutionRefsArray.length()) {
                    solutionRefs.add(solutionRefsArray.getString(s))
                }
            }

            val highlights = mutableListOf<String>()
            val highlightsArray = obj.optJSONArray("highlight_squares")
            if (highlightsArray != null) {
                for (h in 0 until highlightsArray.length()) {
                    highlights.add(highlightsArray.getString(h))
                }
            }

            val related = mutableListOf<String>()
            val relatedArray = obj.optJSONArray("related_diagram_ids")
            if (relatedArray != null) {
                for (r in 0 until relatedArray.length()) {
                    related.add(relatedArray.getString(r))
                }
            }

            val visualConf = obj.optString("visual_confidence", "high")
            val notes = obj.optString("implementation_notes", "")

            // Audit status evaluation
            val (auditStatus, auditWarning) = if (id == "BD-027") {
                Pair(
                    AuditStatus.BLOCKED_NEEDS_HUMAN_REVIEW,
                    "⚠️ اخطار ممیزی شطرنج: این دیاگرام طبق ادعای کپشن مات نیست! فیل g7 به شاه h8 کیش می‌دهد، اما خانه g8 برای فرار قانونی باز است (1... Kg8). این مورد مسدود شده و تا زمان بازبینی انسانی به عنوان مات ارائه نمی‌شود."
                )
            } else {
                Pair(AuditStatus.VALID, null)
            }

            result.add(
                DiagramItem(
                    diagramId = id,
                    pdfPage = page,
                    sectionName = section,
                    bookContext = bookContext,
                    diagramKind = diagramKind,
                    recommendedInteraction = interaction,
                    initialFen = fen,
                    currentFen = fen,
                    sideToMove = sideToMove,
                    moveHistory = moveHistory,
                    pgnRef = pgnRef,
                    solutionPgnRefs = solutionRefs,
                    highlightSquares = highlights,
                    relatedDiagramIds = related,
                    visualConfidence = visualConf,
                    implementationNotes = notes,
                    auditStatus = auditStatus,
                    auditWarning = auditWarning
                )
            )
        }

        return result
    }

    /**
     * اسکلت اولیه ساختاری بخش‌های شش‌گانه فصل اول:
     * چیدمان فعلی `orderedContentBlocks` در هر بخش یک اسکلت اولیه است (نه ترتیب نهایی پاراگراف‌به‌پاراگراف کتاب).
     * فهرست و ترتیب نهایی بلوک‌های متنی و نمودارها بدون حدس زدن هیچ متنی، پس از تحویل رسمی توسط مدیر پروژه جایگزین/تکمیل می‌شود.
     */
    private fun loadSectionsFromAssets(): List<SectionItem> {
        // اطلاعات انگلیسی (titleEn) صرفاً برای ارجاع داخلی حفظ شده و در رابط کاربری نمایش داده نمی‌شود.
        // پیام فارسی قسمت‌های بدون ترجمه: محتوای این بخش در حال آماده‌سازی است.
        val pendingNotice = "محتوای این بخش در حال آماده‌سازی است."
        return listOf(
            SectionItem(
                sectionId = "SEC_CH01_01_OPENING",
                index = 0,
                titleEn = "Chapter opening",
                titleFa = "شروع فصل",
                pdfPages = listOf(40),
                diagramIds = listOf("BD-025"),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_01_OPENING",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_01_OPENING_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_01_OPENING_D025",
                            diagramId = "BD-025"
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            ),
            SectionItem(
                sectionId = "SEC_CH01_02_CHECKMATE",
                index = 1,
                titleEn = "Checkmate",
                titleFa = "مات",
                pdfPages = listOf(41, 42, 43, 44, 45),
                diagramIds = listOf("BD-026", "BD-027", "BD-028", "BD-029", "BD-030"),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_02_CHECKMATE",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_D026",
                            diagramId = "BD-026"
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_D027",
                            diagramId = "BD-027"
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_D028",
                            diagramId = "BD-028"
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_D029",
                            diagramId = "BD-029"
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_02_CHECKMATE_D030",
                            diagramId = "BD-030"
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            ),
            SectionItem(
                sectionId = "SEC_CH01_03_RESIGNATION",
                index = 2,
                titleEn = "Resignation",
                titleFa = "تسلیم",
                pdfPages = listOf(45, 46),
                diagramIds = listOf("BD-031"),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_03_RESIGNATION",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_03_RESIGNATION_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_03_RESIGNATION_D031",
                            diagramId = "BD-031"
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            ),
            SectionItem(
                sectionId = "SEC_CH01_04_TIMING_OUT",
                index = 3,
                titleEn = "Timing Out",
                titleFa = "اتمام زمان",
                pdfPages = listOf(47, 48),
                diagramIds = listOf("BD-032", "BD-033"),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_04_TIMING_OUT",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_04_TIMING_OUT_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.ExerciseBlock(
                            blockId = "SEC_CH01_04_TIMING_OUT_E032",
                            diagramId = "BD-032"
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_04_TIMING_OUT_D033",
                            diagramId = "BD-033"
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            ),
            SectionItem(
                sectionId = "SEC_CH01_05_ABANDONMENT",
                index = 4,
                titleEn = "Abandonment",
                titleFa = "ترک بازی",
                pdfPages = listOf(48, 49),
                diagramIds = listOf("BD-034"),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_05_ABANDONMENT",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_05_ABANDONMENT_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.DiagramBlock(
                            blockId = "SEC_CH01_05_ABANDONMENT_D034",
                            diagramId = "BD-034"
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            ),
            SectionItem(
                sectionId = "SEC_CH01_06_SUMMARY",
                index = 5,
                titleEn = "Summary and additional lesson link",
                titleFa = "خلاصه",
                pdfPages = listOf(50),
                diagramIds = emptyList(),
                translationSlot = TranslationSlot(
                    sectionId = "SEC_CH01_06_SUMMARY",
                    orderedContentBlocks = listOf(
                        SectionContentBlock.ParagraphBlock(
                            blockId = "SEC_CH01_06_SUMMARY_P001",
                            textFa = null,
                            showPlaceholderWhenEmpty = true
                        ),
                        SectionContentBlock.ExternalReferenceBlock(
                            blockId = "SEC_CH01_06_SUMMARY_EXT001",
                            titleFa = null,
                            url = null,
                            isAuthorizedAndEnabled = false
                        )
                    ),
                    placeholderNotice = pendingNotice,
                    translationStatus = TranslationStatus.PENDING
                )
            )
        )
    }

    private fun loadPgnSequencesFromAssets(): Map<String, PgnSequence> {
        val map = mutableMapOf<String, PgnSequence>()
        try {
            val jsonString = context.assets.open("chapter_01_pgn_steps.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                val id = obj.getString("id")
                val desc = obj.optString("description", "")
                val movesJson = obj.getJSONArray("moves")
                val fensJson = obj.getJSONArray("fens")

                val moves = mutableListOf<String>()
                for (m in 0 until movesJson.length()) {
                    moves.add(movesJson.getString(m))
                }

                val fens = mutableListOf<String>()
                for (f in 0 until fensJson.length()) {
                    fens.add(fensJson.getString(f))
                }

                map[key] = PgnSequence(id, desc, moves, fens)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }
}
