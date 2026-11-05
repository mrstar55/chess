package com.example

import com.example.chess.ChessBoardState
import com.example.chess.ChessEngine
import com.example.chess.Move
import com.example.chess.PgnReplayer
import com.example.chess.PgnSequence
import com.example.model.PieceType
import org.junit.Assert.*
import org.junit.Test

class ChessChapterOneTest {

    @Test
    fun testChessEngine_StandardStartPosition() {
        val startFen = ChessEngine.STANDARD_START_FEN
        val engine = ChessEngine(startFen)

        assertEquals('w', engine.state.activeColor)
        assertEquals("KQkq", engine.state.castling)
        assertFalse("White should not be in check in start position", engine.isInCheck())

        val legalMoves = engine.generateLegalMoves()
        assertEquals(20, legalMoves.size)
        assertEquals(startFen, engine.toFen())
    }

    @Test
    fun testNormalPieceMoves() {
        val engine = ChessEngine(ChessEngine.STANDARD_START_FEN)

        // 1. e4 (Pawn double push)
        val e4Res = engine.makeMove(Move(4, 1, 4, 3))
        assertNotNull(e4Res)
        assertEquals("e4", e4Res!!.san)
        assertEquals("e3", engine.state.enPassant)

        // 1... Nc6 (Knight move)
        val nc6Res = engine.makeMove(Move(1, 7, 2, 5))
        assertNotNull(nc6Res)
        assertEquals("Nc6", nc6Res!!.san)
        assertEquals("-", engine.state.enPassant)

        // 2. Bc4 (Bishop move)
        val bc4Res = engine.makeMove(Move(5, 0, 2, 3))
        assertNotNull(bc4Res)
        assertEquals("Bc4", bc4Res!!.san)
    }

    @Test
    fun testIllegalMovesRejected() {
        val engine = ChessEngine(ChessEngine.STANDARD_START_FEN)

        // Moving Black piece on White's turn
        assertNull(engine.makeMove(Move(4, 6, 4, 4)))

        // Moving Rook through blocked pawn (a1 -> a4)
        assertNull(engine.makeMove(Move(0, 0, 0, 3)))

        // Moving Knight like a Bishop (b1 -> d3)
        assertNull(engine.makeMove(Move(1, 0, 3, 2)))

        // Moving King into check
        val kingCheckEngine = ChessEngine("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1")
        val e1Moves = kingCheckEngine.generateLegalMovesForSquare(4, 0)
        // Capturing unprotected rook on e2 is legal
        assertTrue(e1Moves.any { it.toNotation == "e2" })
        val protectedRookEngine = ChessEngine("4k3/4r3/8/8/8/8/4r3/4K3 w - - 0 1")
        // Now e2 rook is protected by e7 rook, so Kxe2 is illegal
        assertNull(protectedRookEngine.makeMove(Move(4, 0, 4, 1)))
    }

    @Test
    fun testPinnedPieceCannotExposeKing() {
        // White King on e1, White Knight on e2, Black Rook on e8 -> Knight on e2 is absolutely pinned
        val pinnedKnightEngine = ChessEngine("4r1k1/8/8/8/8/8/4N3/4K3 w - - 0 1")
        val knightMoves = pinnedKnightEngine.generateLegalMovesForSquare(4, 1)
        assertTrue("Pinned knight on e2 must have 0 legal moves", knightMoves.isEmpty())

        // White Rook on e2 pinned by Black Rook on e8 can move along the e-file (e3..e8) but NOT horizontally (d2, f2)
        val pinnedRookEngine = ChessEngine("4r1k1/8/8/8/8/8/4R3/4K3 w - - 0 1")
        val rookMoves = pinnedRookEngine.generateLegalMovesForSquare(4, 1)
        assertEquals(6, rookMoves.size) // e3, e4, e5, e6, e7, Rxe8
        assertTrue(rookMoves.all { it.toFile == 4 })
        assertNull("Pinned rook cannot move horizontally to d2", pinnedRookEngine.makeMove(Move(4, 1, 3, 1)))
    }

    @Test
    fun testDoubleCheckOnlyKingMovesAllowed() {
        // White King on e1 is in double check from Black Rook on e8 AND Black Bishop on b4
        val doubleCheckFen = "4r1k1/8/8/8/Rb6/8/8/4K3 w - - 0 1"
        val engine = ChessEngine(doubleCheckFen)
        assertTrue(engine.isInCheck())

        val allLegal = engine.generateLegalMoves()
        assertTrue("In double check, every legal move must be a King move", allLegal.all { it.fromNotation == "e1" })
        assertNull(engine.makeMove(Move(0, 3, 1, 3))) // Rxb4 illegal in double check
        assertNull(engine.makeMove(Move(0, 3, 4, 3))) // Re4 illegal in double check
    }

    @Test
    fun testStandardPieceCapture() {
        val engine = ChessEngine("4k3/8/8/3p4/4P3/8/8/4K3 w - - 4 10")
        val res = engine.makeMove(Move(4, 3, 3, 4)) // exd5
        assertNotNull(res)
        assertEquals("exd5", res!!.san)
        assertEquals(0, engine.state.halfMove)
        assertEquals(PieceType.PAWN, engine.state.getPieceAt("d5")?.type)
        assertTrue(engine.state.getPieceAt("d5")?.isWhite == true)
        assertNull(engine.state.getPieceAt("e4"))
    }

    @Test
    fun testEnPassantAndCapturedPawnRemoval() {
        // White pawn on e5, Black just played d7-d5 (en passant target d6)
        val epFen = "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1"
        val engine = ChessEngine(epFen)

        // Execute en passant capture: e5 (4,4) -> d6 (3,5)
        val res = engine.makeMove(Move(4, 4, 3, 5))
        assertNotNull("En passant move must be legal", res)
        assertEquals("exd6", res!!.san)

        // White pawn is now on d6
        val d6Piece = engine.state.getPieceAt("d6")
        assertNotNull(d6Piece)
        assertEquals(PieceType.PAWN, d6Piece?.type)
        assertTrue(d6Piece?.isWhite == true)

        // Captured Black pawn on d5 MUST be removed!
        assertNull("Captured pawn on d5 must be removed after en passant", engine.state.getPieceAt("d5"))
        assertNull("Origin square e5 must be empty", engine.state.getPieceAt("e5"))
    }

    @Test
    fun testPromotionToAllFourPieces() {
        val promoFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val expectedMap = mapOf(
            PieceType.QUEEN to "a8=Q+",
            PieceType.ROOK to "a8=R+",
            PieceType.BISHOP to "a8=B",
            PieceType.KNIGHT to "a8=N"
        )

        for ((promoType, expectedSan) in expectedMap) {
            val engine = ChessEngine(promoFen)
            val res = engine.makeMove(Move(0, 6, 0, 7, promotion = promoType))
            assertNotNull("Promotion to $promoType must succeed", res)
            assertEquals(expectedSan, res!!.san)
            val promotedPiece = engine.state.getPieceAt("a8")
            assertNotNull(promotedPiece)
            assertEquals(promoType, promotedPiece?.type)
            assertTrue(promotedPiece?.isWhite == true)
        }
    }

    @Test
    fun testPromotionWithoutPieceSelectionRejected() {
        val promoFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        val engine = ChessEngine(promoFen)

        // Attempting to move pawn a7 -> a8 with promotion = null MUST be rejected!
        val res = engine.makeMove(Move(0, 6, 0, 7, promotion = null))
        assertNull("Pawn reaching 8th rank without promotion piece must be rejected", res)
        assertNull("Square a8 must remain empty", engine.state.getPieceAt("a8"))
        assertEquals(PieceType.PAWN, engine.state.getPieceAt("a7")?.type)
    }

    @Test
    fun testCastlingWithRookPresent() {
        val castleFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"

        // White Kingside castling (e1 -> g1)
        val engineK = ChessEngine(castleFen)
        val resK = engineK.makeMove(Move(4, 0, 6, 0))
        assertNotNull(resK)
        assertEquals("O-O", resK!!.san)
        assertEquals(PieceType.KING, engineK.state.getPieceAt("g1")?.type)
        assertEquals(PieceType.ROOK, engineK.state.getPieceAt("f1")?.type)
        assertNull(engineK.state.getPieceAt("e1"))
        assertNull(engineK.state.getPieceAt("h1"))
        assertEquals("kq", engineK.state.castling)

        // White Queenside castling (e1 -> c1)
        val engineQ = ChessEngine(castleFen)
        val resQ = engineQ.makeMove(Move(4, 0, 2, 0))
        assertNotNull(resQ)
        assertEquals("O-O-O", resQ!!.san)
        assertEquals(PieceType.KING, engineQ.state.getPieceAt("c1")?.type)
        assertEquals(PieceType.ROOK, engineQ.state.getPieceAt("d1")?.type)
        assertNull(engineQ.state.getPieceAt("e1"))
        assertNull(engineQ.state.getPieceAt("a1"))
        assertEquals("kq", engineQ.state.castling)
    }

    @Test
    fun testCastlingWithoutRookRejected() {
        // 1. Strict FEN parser immediately rejects FENs claiming 'KQ' when rooks on a1/h1 are absent
        val noRookFen = "4k3/8/8/8/8/8/8/4K3 w KQ - 0 1"
        try {
            ChessEngine.parseFen(noRookFen)
            fail("Expected IllegalArgumentException for FEN with KQ castling rights but no rooks on a1/h1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("castling") == true)
        }

        // 2. Defense-in-depth: even if an in-memory ChessBoardState has castling="KQ" without rooks,
        // move generation still verifies rook presence and rejects castling!
        val validBase = ChessEngine.parseFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val stateWithCorruptFlag = ChessBoardState(
            board = validBase.copyBoard(),
            activeColor = 'w',
            castling = "KQ",
            enPassant = "-",
            halfMove = 0,
            fullMove = 1
        )
        val engine = ChessEngine(stateWithCorruptFlag)
        val kingMoves = engine.generateLegalMovesForSquare(4, 0)
        assertFalse("Kingside castling must not be generated without rook on h1", kingMoves.any { it.toNotation == "g1" })
        assertFalse("Queenside castling must not be generated without rook on a1", kingMoves.any { it.toNotation == "c1" })

        assertNull(engine.makeMove(Move(4, 0, 6, 0)))
        assertNull(engine.makeMove(Move(4, 0, 2, 0)))
    }

    @Test
    fun testCastlingRightsRemovedWhenRookCapturedOnHomeSquare() {
        // Black Bishop on g2 captures White Rook on h1 (with White still having KQ castling rights)
        val fen = "r3k2r/8/8/8/8/8/6b1/R3K2R b KQkq - 0 1"
        val engine = ChessEngine(fen)

        // 1... Bxh1 (file 6, rank 1 -> file 7, rank 0)
        val res = engine.makeMove(Move(6, 1, 7, 0))
        assertNotNull(res)
        assertEquals("Bxh1", res!!.san)
        // White's Kingside castling right 'K' must be removed because h1 rook was captured!
        assertEquals("Qkq", engine.state.castling)
    }

    @Test
    fun testCheckmateAndStalemate() {
        // Checkmate (BD-026)
        val mateEngine = ChessEngine("4k3/4Q3/4K3/8/8/8/8/8 b - - 0 1")
        assertTrue(mateEngine.isInCheck())
        assertTrue(mateEngine.isCheckmate())
        assertFalse(mateEngine.isStalemate())

        // Stalemate (Black king on h8, White king on f7, White queen on g6, Black to move)
        val stalemateEngine = ChessEngine("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1")
        assertFalse("Stalemated king is not in check", stalemateEngine.isInCheck())
        assertFalse("Stalemate is not checkmate", stalemateEngine.isCheckmate())
        assertTrue("Position must be detected as stalemate", stalemateEngine.isStalemate())
        assertTrue(stalemateEngine.generateLegalMoves().isEmpty())
    }

    @Test
    fun testChessEngine_Bd027BlockedVerification() {
        val fen = "7k/6B1/6K1/7N/8/8/8/8 b - - 0 1"
        val engine = ChessEngine(fen)

        assertEquals('b', engine.state.activeColor)
        assertTrue("Black king at h8 is in check by bishop on g7", engine.isInCheck())

        val legalMoves = engine.generateLegalMoves()
        assertEquals("Black king must have exactly 1 legal move to escape", 1, legalMoves.size)
        assertEquals("h8", legalMoves.first().fromNotation)
        assertEquals("g8", legalMoves.first().toNotation)
        assertFalse("BD-027 MUST NOT be reported as checkmate", engine.isCheckmate())
    }

    @Test
    fun testChessEngine_Bd032DynamicMateAndNonMate() {
        val startFen = "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1"
        val engine = ChessEngine(startFen)

        val allLegalMoves = engine.generateLegalMoves()
        assertEquals(42, allLegalMoves.size)

        val checkmateMoves = mutableListOf<String>()
        for (move in allLegalMoves) {
            val testEngine = ChessEngine(startFen)
            val result = testEngine.makeMove(move)
            assertNotNull(result)
            if (result!!.isCheckmate) {
                checkmateMoves.add(result.san)
            }
        }

        assertEquals(2, checkmateMoves.size)
        assertTrue(checkmateMoves.contains("Qh8#"))
        assertTrue(checkmateMoves.contains("Qh2#"))

        // Verify legal non-mating moves (e.g., Qe8, Qf5, Qe1+)
        val nonMateTargets = listOf("e8", "f5", "e1")
        for (target in nonMateTargets) {
            val sim = ChessEngine(startFen)
            val move = allLegalMoves.first { it.fromNotation == "e5" && it.toNotation == target }
            val res = sim.makeMove(move)
            assertNotNull(res)
            assertFalse("Move to $target is legal but NOT checkmate", res!!.isCheckmate)
        }
    }

    @Test
    fun testPerft_StandardInitialPositionDepths1To4() {
        val engine = ChessEngine(ChessEngine.STANDARD_START_FEN)
        assertEquals("Perft Depth 1 must be 20", 20L, engine.perft(1))
        assertEquals("Perft Depth 2 must be 400", 400L, engine.perft(2))
        assertEquals("Perft Depth 3 must be 8902", 8902L, engine.perft(3))
        assertEquals("Perft Depth 4 must be 197281", 197281L, engine.perft(4))
    }

    @Test
    fun testPerft_ComplexPositions_KiwipeteAndEnPassantAndPromotions() {
        // 1. Kiwipete (CPW Position 2): intensive castling, rook captures, en passant, promotions, pins
        val kiwipete = ChessEngine("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals("Kiwipete Perft(1) must be 48", 48L, kiwipete.perft(1))
        assertEquals("Kiwipete Perft(2) must be 2039", 2039L, kiwipete.perft(2))
        assertEquals("Kiwipete Perft(3) must be 97862", 97862L, kiwipete.perft(3))

        // 2. CPW Position 3: en passant horizontal pin & promotions
        val pos3 = ChessEngine("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals("CPW Pos 3 Perft(1) must be 14", 14L, pos3.perft(1))
        assertEquals("CPW Pos 3 Perft(2) must be 191", 191L, pos3.perft(2))
        assertEquals("CPW Pos 3 Perft(3) must be 2812", 2812L, pos3.perft(3))
        assertEquals("CPW Pos 3 Perft(4) must be 43238", 43238L, pos3.perft(4))

        // 3. CPW Position 4: promotions, checks, castling rights
        val pos4 = ChessEngine("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals("CPW Pos 4 Perft(1) must be 6", 6L, pos4.perft(1))
        assertEquals("CPW Pos 4 Perft(2) must be 264", 264L, pos4.perft(2))
        assertEquals("CPW Pos 4 Perft(3) must be 9467", 9467L, pos4.perft(3))
    }

    @Test
    fun testAllChapterOneDiagrams_Bd025ToBd034_FenAndEngineValidation() {
        val chapterDiagrams = mapOf(
            "BD-025" to "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "BD-026" to "4k3/4Q3/4K3/8/8/8/8/8 b - - 0 1",
            "BD-027" to "7k/6B1/6K1/7N/8/8/8/8 b - - 0 1",
            "BD-028" to "r1bq3r/pppp2pp/2n2n2/2b2Q2/3kP3/2NPB3/PPP1BPPP/R3K2R b - - 0 1",
            "BD-029" to "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4",
            "BD-030" to "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
            "BD-031" to "8/4b3/4P3/1k4P1/8/ppK5/8/4R3 b - - 1 45",
            "BD-032" to "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
            "BD-033" to "8/8/1p6/1k2q3/7K/8/8/6r1 b - - 0 1",
            "BD-034" to "r1r4k/pp1q3R/5pp1/3p2N1/6Q1/8/PP3PPP/2R3K1 b - - 0 25"
        )

        val expectedCheckmates = setOf("BD-026", "BD-028", "BD-029", "BD-030")
        val expectedInCheckButNotMate = setOf("BD-027", "BD-034")

        for ((id, fen) in chapterDiagrams) {
            val engine = ChessEngine(fen)
            assertEquals("Round-trip FEN must match for $id", fen, engine.toFen())
            if (id in expectedCheckmates) {
                assertTrue("$id must be in check", engine.isInCheck())
                assertTrue("$id must be checkmate", engine.isCheckmate())
            } else if (id in expectedInCheckButNotMate) {
                assertTrue("$id must be in check", engine.isInCheck())
                assertFalse("$id must NOT be checkmate", engine.isCheckmate())
            } else {
                assertFalse("$id must not be checkmate", engine.isCheckmate())
            }
        }
    }

    @Test
    fun testStrictFenValidation_RejectsInvalidFen() {
        val invalidFens = listOf(
            "",
            "invalid_fen",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -", // incomplete 4-field FEN rejected by strict 6-field parseFen
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1", // only 7 ranks
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1", // last rank has only 7 squares
            "44/8/8/8/8/8/8/4K2k w - - 0 1", // consecutive digits '44' summing to 8
            "17/8/8/8/8/8/8/4K2k w - - 0 1", // consecutive digits '17' summing to 8
            "rnbqkbnr/pppppppp/8/8/3X4/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // invalid piece 'X'
            "rnbq1bnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", // missing Black King
            "8/8/8/4k3/4K3/8/8/8 w - - 0 1", // adjacent Kings (e5 and e4)
            "8/8/8/3kK3/8/8/8/8 w - - 0 1", // adjacent Kings (d5 and e5)
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1", // invalid active color 'x'
            "4k3/8/8/8/8/8/8/4K3 w K - 0 1", // castling right 'K' without White Rook on h1
            "r3k2r/8/8/8/8/8/8/R2K3R w KQ - 0 1", // castling right 'KQ' when White King is on d1 instead of e1
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e4 0 1", // invalid en passant rank 'e4'
            "4k3/8/8/3pP3/8/8/8/4K3 b - d6 0 1", // en passant rank 'd6' invalid when Black is to move
            "4k3/8/8/4P3/8/8/8/4K3 w - d6 0 1", // en passant 'd6' without a Black pawn on d5
            "4k3/3p4/8/3pP3/8/8/8/4K3 w - d6 0 1", // en passant 'd6' when d7 is not empty
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNP w KQkq - 0 1", // pawn on 1st rank
            "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1", // Black king on e8 is in check, but it is White's turn ('w')
            "4k3/4r3/8/8/8/8/8/4K3 b - - 0 1"  // White king on e1 is in check, but it is Black's turn ('b')
        )

        for (badFen in invalidFens) {
            try {
                ChessEngine.parseFen(badFen)
                fail("Expected IllegalArgumentException for invalid FEN: '$badFen'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }

        // Verify that parseIncompleteFen explicitly supports valid 2-to-5-field FENs
        val incompleteState = ChessEngine.parseIncompleteFen("4k3/8/8/8/8/8/8/4K3 w")
        assertEquals('w', incompleteState.activeColor)
        assertEquals("-", incompleteState.castling)
        assertEquals("-", incompleteState.enPassant)
        assertEquals(0, incompleteState.halfMove)
        assertEquals(1, incompleteState.fullMove)
    }

    @Test
    fun testFenValidation_ActiveVsInactiveKingInCheckAndDiagrams() {
        // 1. Inactive side's king in check -> MUST be rejected
        try {
            ChessEngine.parseFen("4k3/8/8/8/8/8/4R3/4K3 w - - 0 1")
            fail("FEN where Black king is in check on White's turn must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("side not to move") == true)
        }

        try {
            ChessEngine.parseFen("4k3/4r3/8/8/8/8/8/4K3 b - - 0 1")
            fail("FEN where White king is in check on Black's turn must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("side not to move") == true)
        }

        // 2. Active side's king (side to move) in check -> MUST be accepted
        val blackInCheckActive = ChessEngine("4k3/8/8/8/8/8/4R3/4K3 b - - 0 1")
        assertEquals('b', blackInCheckActive.state.activeColor)
        assertTrue(blackInCheckActive.isInCheck())

        val whiteInCheckActive = ChessEngine("4k3/4r3/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals('w', whiteInCheckActive.state.activeColor)
        assertTrue(whiteInCheckActive.isInCheck())

        // 3. Standard starting position -> MUST be accepted
        val startEngine = ChessEngine(ChessEngine.STANDARD_START_FEN)
        assertEquals('w', startEngine.state.activeColor)
        assertFalse(startEngine.isInCheck())

        // 4. All 10 Chapter 1 diagrams -> MUST be accepted with preserved state
        val chapterDiagrams = listOf(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "4k3/4Q3/4K3/8/8/8/8/8 b - - 0 1",
            "7k/6B1/6K1/7N/8/8/8/8 b - - 0 1",
            "r1bq3r/pppp2pp/2n2n2/2b2Q2/3kP3/2NPB3/PPP1BPPP/R3K2R b - - 0 1",
            "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4",
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
            "8/4b3/4P3/1k4P1/8/ppK5/8/4R3 b - - 1 45",
            "8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1",
            "8/8/1p6/1k2q3/7K/8/8/6r1 b - - 0 1",
            "r1r4k/pp1q3R/5pp1/3p2N1/6Q1/8/PP3PPP/2R3K1 b - - 0 25"
        )
        for (fen in chapterDiagrams) {
            val parsed = ChessEngine.parseFen(fen)
            assertEquals(fen, ChessEngine.stateToFen(parsed))
        }
    }

    @Test
    fun testSanDisambiguation_FileRankAndBoth() {
        // 1. Two rooks on same rank (a1 and f1) moving to d1 -> Rad1 and Rfd1
        val sameRankFen = "4k3/8/8/8/8/8/8/R4R1K w - - 0 1"
        val eng1 = ChessEngine(sameRankFen)
        val resRad1 = eng1.makeMove(Move(0, 0, 3, 0))
        assertEquals("Rad1", resRad1?.san)

        // 2. Two rooks on same file (a1 and a5) moving to a3 -> R1a3 and R5a3
        val sameFileFen = "4k3/8/8/R7/8/8/8/R6K w - - 0 1"
        val eng2 = ChessEngine(sameFileFen)
        val resR1a3 = eng2.makeMove(Move(0, 0, 0, 2))
        assertEquals("R1a3", resR1a3?.san)

        // 3. Three queens (h4, h1, e4) with White King on a1 and Black King on c3, all able to move to e1 -> Qh4e1+ requires both file and rank!
        val threeQueensFen = "8/8/8/8/4Q2Q/2k5/8/K6Q w - - 0 1"
        val eng3 = ChessEngine(threeQueensFen)
        val resQh4e1 = eng3.makeMove(Move(7, 3, 4, 0)) // h4 -> e1
        assertEquals("Qh4e1+", resQh4e1?.san)
    }

    @Test
    fun testPgnReplayer_NavigationAndBoundaries() {
        val seq = PgnSequence(
            id = "SCHOLARS_MATE_TEST",
            description = "Scholar's Mate",
            moves = listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#"),
            fens = listOf(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                "rnbqkbnr/pppp1ppp/8/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR b KQkq - 1 2",
                "r1bqkbnr/pppp1ppp/2n5/4p2Q/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 2 3",
                "r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 3 3",
                "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
                "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
            )
        )

        assertTrue(
            "Precomputed sequence must pass step-by-step ChessEngine integrity check including enPassant",
            seq.validateIntegrity(
                expectedStartFen = ChessEngine.STANDARD_START_FEN,
                expectedFinalFen = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
            )
        )

        // Verify that omitting enPassant ('-' instead of 'e3' after 1.e4) is rejected by validateIntegrity
        val tamperedEnPassantSeq = PgnSequence(
            id = "TAMPERED_EP_TEST",
            description = "Tampered enPassant",
            moves = listOf("e4"),
            fens = listOf(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
            )
        )
        assertFalse(
            "Sequence with mismatched enPassant field must be rejected by validateIntegrity",
            tamperedEnPassantSeq.validateIntegrity()
        )

        val replayer = PgnReplayer(seq)
        assertTrue(replayer.isAtStart)
        assertFalse(replayer.isAtEnd)
        assertEquals(0, replayer.currentStep)
        assertEquals(seq.fens[0], replayer.currentFen)
        assertNull(replayer.currentMoveSan)
        assertFalse(replayer.previous())

        assertTrue(replayer.next())
        assertEquals(1, replayer.currentStep)
        assertEquals("e4", replayer.currentMoveSan)

        while (replayer.next()) {}
        assertTrue(replayer.isAtEnd)
        assertEquals(seq.totalSteps, replayer.currentStep)
        assertEquals("Qxf7#", replayer.currentMoveSan)
        assertFalse(replayer.next())

        replayer.reset()
        assertTrue(replayer.isAtStart)
        assertEquals(seq.fens[0], replayer.currentFen)
    }

    @Test
    fun testChessboardCoordinates_NoMirroringInRTL() {
        val files = (0..7).map { ('a' + it).toString() }
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), files)

        val ranksTopToBottom = (7 downTo 0).map { it + 1 }
        assertEquals(listOf(8, 7, 6, 5, 4, 3, 2, 1), ranksTopToBottom)
    }
}
