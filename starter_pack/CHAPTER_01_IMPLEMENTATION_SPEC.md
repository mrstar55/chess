# Chapter 1 - Technical asset mapping

**Source:** How to Win at Chess PDF provided in this conversation.
**PDF pages:** physical page numbers, 1-based.
**Chapter range:** PDF pages 39-50.

## Chapter structure

| Order | Section | PDF pages | Diagram IDs |
|---:|---|---:|---|
| 1 | Chapter opening | 40 | BD-025 |
| 2 | Checkmate | 41-45 | BD-026 to BD-030 |
| 3 | Resignation | 45-46 | BD-031 |
| 4 | Timing Out | 47-48 | BD-032, BD-033 |
| 5 | Abandonment | 48-49 | BD-034 |
| 6 | Summary and additional lesson link | 50 | - |

## Diagram inventory

| ID | PDF page | XREF | Content | Interaction | Side to move |
|---|---:|---:|---|---|---|
| BD-025 | 40 | 1097 | starting_position | static_position_with_optional_free_explore | white |
| BD-026 | 41 | 1101 | checkmate_example_3_pieces | static_explanatory_position | black |
| BD-027 | 42 | 1103 | checkmate_example_4_pieces | static_explanatory_position | black |
| BD-028 | 43 | 1105 | full_board_checkmate_example | static_explanatory_position | black |
| BD-029 | 44 | 1107 | named_mate_pattern | step_by_step_replay_then_static_mate | black |
| BD-030 | 45 | 1109 | named_mate_pattern | step_by_step_replay_then_static_mate | white |
| BD-031 | 46 | 1113 | historical_game_position | full_game_replay_to_resignation_position | black |
| BD-032 | 47 | 1115 | mate_in_one_exercise_two_solutions | interactive_mate_in_one_exercise | black |
| BD-033 | 48 | 1118 | insufficient_mating_material_comparison | static_comparison_with_BD032 | black (illustrative snapshot) |
| BD-034 | 49 | 1121 | historical_game_position | full_game_replay_to_abandonment_position | black |

## FEN positions

All FEN strings below are board data reconstructed from the page images. The final fields (halfmove/fullmove counters) are preserved where supported by the historical game score; for static positions they are neutral placeholders.

### BD-025 - PDF page 40

The chapter recalls the standard initial chess position before explaining how games are won.

**FEN:** `rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1`
**FEN status:** reconstructed_standard_start_position
**Highlights:** none recorded
**Implementation:** This exact image asset is reused on PDF page 55 (Chapter Two). In the app render the board from FEN; do not ship the source bitmap as the board UI.

### BD-026 - PDF page 41

The simplest checkmate example: Black king e8, White queen e7, White king e6.

**FEN:** `4k3/4Q3/4K3/8/8/8/8/8 b - - 0 1`
**FEN status:** reconstructed_from_board_image_and_caption
**Highlights:** none recorded
**Implementation:** Position is shown as a checkmate example. No move sequence is printed for reaching it. Use as a static teaching position unless a separate move sequence is authored and reviewed.

### BD-027 - PDF page 42

Four-piece checkmate: White bishop g7, White king g6, White knight h6; Black king h8.

**FEN:** `7k/6B1/6K1/7N/8/8/8/8 b - - 0 1`
**FEN status:** reconstructed_from_board_image_and_caption
**Highlights:** h8
**Implementation:** Black king square h8 is highlighted in red in the source diagram. The caption explains the White bishop attack and king/knight coverage.

### BD-028 - PDF page 43

A fully populated-board checkmate example; Black king is on d4 and is attacked by White's pieces.

**FEN:** `r1bq3r/pppp2pp/2n2n2/2b2Q2/3kP3/2NPB3/PPP1BPPP/R3K2R b - - 0 1`
**FEN status:** reconstructed_from_board_image_and_caption
**Highlights:** d4, e3
**Implementation:** FEN castling rights deliberately set to '-' because the diagram does not provide the prior move history needed to prove whether White retained castling rights. Do not infer castling rights from piece placement alone.

### BD-029 - PDF page 44

The final Scholar's Mate position after White checkmates Black in four moves.

**FEN:** `r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4`
**FEN status:** reconstructed_from_board_image_and_matching_canonical_line
**Highlights:** e8, f7
**Implementation:** The PGN line is an added instructional reconstruction, not quoted from this chapter. Validate the replay in a chess rules engine before release.

### BD-030 - PDF page 45

Fool's Mate: Black queen on h4 has checkmated the White king in two moves.

**FEN:** `rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3`
**FEN status:** reconstructed_from_board_image_and_matching_canonical_line
**Highlights:** e1, h4
**Implementation:** The PGN line is an added instructional reconstruction, not quoted from this chapter. Validate the replay in a chess rules engine before release.

### BD-031 - PDF page 46

The resignation position from Game 11 of the 2014 World Chess Championship, Carlsen (White) vs Anand (Black).

**FEN:** `8/4b3/4P3/1k4P1/8/ppK5/8/4R3 b - - 1 45`
**FEN status:** reconstructed_from_board_image_and_matched_to_external_game_score
**Highlights:** none recorded
**Implementation:** The full game PGN is sourced externally because the chapter itself only shows the final position. External source: https://www.schakers.info/?p=5864. The game ended 1-0 after 45.Kc3.

### BD-032 - PDF page 47

Black has a large material advantage; the text asks the reader to find either of two checkmates if it were Black to move.

**FEN:** `8/8/1p6/1k2q1P1/7K/8/8/6r1 b - - 0 1`
**FEN status:** reconstructed_from_board_image_and_confirmed_by_book_footnote
**Highlights:** none recorded
**Implementation:** Exercise prompt and both solutions are source-supported. Accept either move as correct. Add feedback only after the move is made.

### BD-033 - PDF page 48

Almost the same position as the preceding diagram, except White no longer has the pawn on g5; the text explains a draw if Black flags.

**FEN:** `8/8/1p6/1k2q3/7K/8/8/6r1 b - - 0 1`
**FEN status:** reconstructed_from_board_image_and_explicit_comparison_text
**Highlights:** none recorded
**Implementation:** The difference from BD-032 is exactly the missing White pawn on g5. No playable sequence is needed; present as a visual comparison.

### BD-034 - PDF page 49

The Steinitz–von Bardeleben position from Hastings 1895, after White's rook move to h7+; von Bardeleben left the playing hall.

**FEN:** `r1r4k/pp1q3R/5pp1/3p2N1/6Q1/8/PP3PPP/2R3K1 b - - 0 25`
**FEN status:** reconstructed_from_board_image_and_matched_to_external_game_score
**Highlights:** none recorded
**Implementation:** External FIDE Museum material confirms Black to move for the famous position; an external historical PGN gives the played score through 25.Rxh7+. External sources: https://museum.fide.com/champions/wilhelm-steinitz and https://www.devonjuniorchess.co.uk/content/hastings-1895. Do not append the post-game demonstration variation as if it were played.

## PGN and move-sequence decisions

1. **BD-029 / Scholar’s Mate:** a canonical candidate line is supplied in the PGN file. The chapter shows the final checkmate and states four moves, but does not print the full move sequence. Treat the line as added teaching data and replay-validate it before release.
2. **BD-030 / Fool’s Mate:** a canonical candidate line is supplied. The chapter says the mate occurs in two moves and shows the final board, but does not print the moves. Treat the line as added teaching data and replay-validate it before release.
3. **BD-032 / Timing Out exercise:** the source explicitly asks the reader to find two checkmates and its footnote names Qh8 and Qh2. Both solutions are encoded as separate PGN snippets.
4. **BD-031 / Carlsen-Anand 2014 Game 11:** the book shows the resignation position but not the whole score. The external PGN file replays the historical game through 45.Kc3, after which Anand resigned. External score: https://www.schakers.info/?p=5864.
5. **BD-034 / Steinitz-von Bardeleben, Hastings 1895:** the book shows the abandonment position. The external PGN replays the played game through 25.Rxh7+. Do not show post-game demonstration moves as played moves. External score: https://www.devonjuniorchess.co.uk/content/hastings-1895. FIDE Museum confirms Black to move: https://museum.fide.com/champions/wilhelm-steinitz.

## Recommended implementation policy

- Render every chessboard from FEN with the app chessboard component; do not embed screenshots of the original board diagrams as the main UI.
- Use step-by-step replay only where a PGN is supplied and validated. For the 3-piece, 4-piece and full-board mate examples, use explanatory static positions until an authored and reviewed move sequence exists.
- BD-032 is an interactive mate-in-one exercise with two accepted answers: Qh8# and Qh2#.
- BD-033 should be displayed as a comparison with BD-032; the only board difference is removal of White’s pawn on g5.
- BD-031 and BD-034 may offer full-game replay based on external historical game scores. Clearly label these replays as supplementary context, not as move sequences printed in the book.
- Keep the Chessly chapter link as an external reference or replace it with separately created exercises. Do not copy or host Chessly lesson content without rights.
- Before release, replay-check all PGNs using a chess rules library (e.g. python-chess or a tested Android chess library) and compare the ending FEN to the corresponding diagram FEN.

## Validation status / limitations

- 10/10 Chapter 1 diagram images matched to the inventory IDs BD-025 through BD-034.
- FEN board ranks and exactly-one-king-per-side checks passed for all 10 positions.
- The FEN placements were manually reconstructed from the board images. This is not a substitute for full chess-engine legality validation.
- The two named-mate candidate PGNs require rules-engine validation before being presented as definitive interactive replay.
- The book page descriptions and move content were not silently supplemented; the two historical full-game replays are clearly marked as external additions.
- This document is a technical production aid and does not grant any copyright or reproduction permission.
