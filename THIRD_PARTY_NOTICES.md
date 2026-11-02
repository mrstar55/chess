# Third-Party Notices & Chess Engine Library Evaluation

## 1. Empirical Evaluation of `io.github.cvb941:kchesslib:1.0.5` vs Project `ChessEngine`

During the quality control phases, we conducted a direct empirical evaluation of **`io.github.cvb941:kchesslib:1.0.5`** from Maven Central alongside the project's **`ChessEngine`**.

### 1.1 Artifact & License Verification
- **Maven Coordinate:** `io.github.cvb941:kchesslib:1.0.5` (published on Maven Central: `https://central.sonatype.com/artifact/io.github.cvb941/kchesslib/1.0.5`)
- **Repository:** `https://github.com/cvb941/kchesslib` (Kotlin Multiplatform port of `bhlangonijr/chesslib`)
- **License:** **Apache License 2.0** (permissive; allows commercial use, modification, and distribution)

### 1.2 Separation of JVM Execution vs. Android D8 Compilation, JDK 17 vs. JDK 21, and Kotlin Compatibility

When `implementation("io.github.cvb941:kchesslib:1.0.5")` is added to `app/build.gradle.kts`, Gradle resolves the following dependency tree on `debugRuntimeClasspath`:
```text
\--- io.github.cvb941:kchesslib:1.0.5
     \--- io.github.cvb941:kchesslib-jvm:1.0.5
          +--- org.jetbrains.kotlin:kotlin-stdlib:2.2.21
          +--- org.jetbrains.kotlinx:kotlinx-datetime:0.7.1
          |    \--- org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1
          \--- org.jetbrains.kotlinx:kotlinx-io-core:0.8.2
               \--- org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.8.2
                    \--- org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.8.2
```

1. **Bytecode Inspection (`javap -v` Reproducible Output):**
   Inspecting `kchesslib-jvm-1.0.5.jar` directly with `javap -v` confirms that its classes are compiled targeting **Java 21 bytecode (`major version: 65`, class file version `65.0`)**:
   ```text
   Classfile jar:file:///.../kchesslib-jvm-1.0.5.jar!/com/github/bhlangonijr/chesslib/Board.class
     minor version: 0
     major version: 65
     flags: (0x0031) ACC_PUBLIC, ACC_FINAL, ACC_SUPER
   RuntimeVisibleAnnotations:
     0: #705(#706=[I#707,I#707,I#708], ... )
       kotlin.Metadata(mv=[2, 2, 0], k=1, xi=48, ...)
   ```
2. **JVM Unit Test Execution (`testDebugUnitTest`) vs. JDK 17:**
   - Because `kchesslib-jvm-1.0.5.jar` contains **class file version `65.0` (Java 21)**, executing JVM unit tests (`./gradlew testDebugUnitTest`) on a **JDK 17 JVM** (which supports at most class file version `61.0`) fails at class loading with `java.lang.UnsupportedClassVersionError: com/github/bhlangonijr/chesslib/Board has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 61.0`.
   - Executing JVM unit tests with `kchesslib-jvm:1.0.5` succeeds only when the test JVM runs on **JDK 21+**.
3. **Android Compilation with D8 (`assembleDebug` / `dexBuilderDebug`):**
   - In the Android build pipeline, **D8** converts `.class` files into Dalvik Executable (`.dex`) format and desugars Java bytecode features for `minSdk = 24`.
   - When the Gradle daemon runs on **JDK 21**, D8 successfully processes `kchesslib-jvm-1.0.5.jar` (`major version: 65`) and packages it into `classes.dex` for Android runtime execution.
4. **Kotlin & Android Dependency Footprint:**
   - **Kotlin Version Alignment:** The project uses Kotlin Gradle Plugin **`2.2.10`**, whereas `kchesslib:1.0.5` was compiled with Kotlin **`2.2.21`** (`mv = [2, 2, 0]`), forcing a transitive upgrade of `org.jetbrains.kotlin:kotlin-stdlib` from `2.2.10` to `2.2.21`.
   - **Transitive Libraries & Assets:** Pulls in `kotlinx-datetime-jvm:0.7.1`, `kotlinx-io-core-jvm:0.8.2`, `kotlinx-io-bytestring-jvm:0.8.2`, and bundles an uncompressed `358,880`-byte `eco.pgn` file inside the APK root.

### 1.3 Functional & Test Suite Comparison (`KChessLib 1.0.5` vs. Project `ChessEngine`)

| Test / Capability | `io.github.cvb941:kchesslib:1.0.5` | Project `ChessEngine` (`ChessRulesAdapter`) |
|---|---|---|
| Standard Legal Moves & Perft(1..4) | PASS (`20, 400, 8902, 197281`) | PASS (`20, 400, 8902, 197281`) |
| Complex Perft (Kiwipete, Pos 3, Pos 4) | PASS | PASS (`97862`, `43238`, `9467`) |
| `BD-032` Mate-in-1 (`Qh8#`, `Qh2#`) | PASS | PASS |
| `BD-027` Escapable Check (`1...Kg8`) | PASS | PASS |
| En Passant Capture & Pawn Removal | PASS | PASS |
| Promotion to `Q, R, B, N` | PASS | PASS |
| Castling Rights Removed on Rook Capture | PASS | PASS |
| **Castling Without Rook on Board (`4k3/8/8/8/8/8/8/4K3 w KQ - 0 1`)** | **FAIL** (`generateCastleMoves` generates `e1g1` & `e1c1` without checking if rooks exist on `a1`/`h1`) | **PASS** (Rejected both in `parseFen` and in `generateKingMoves`) |
| **Strict FEN Validation (7-square rank, `44` consecutive digits, pawn on rank 1/8, missing king, adjacent kings, inactive king in check, invalid en-passant)** | **FAIL** (`loadFromFen` silently accepts malformed ranks, pawns on rank 1, adjacent kings, and inactive king in check without throwing) | **PASS** (Strictly validated in `parseFen` with `IllegalArgumentException`) |
| **JDK 17 JVM Test Compatibility** | **FAIL** (`major version: 65` requires JDK 21 JVM runtime) | **PASS** (`JavaVersion.VERSION_11` bytecode compatible with JDK 17+) |

**Conclusion:**
As instructed, `KChessLib` has not been added to the project dependencies. The project retains its pure-Kotlin `ChessEngine` behind `ChessRulesAdapter`, which passes all strict FEN validations (including rejecting positions where the inactive side's king is in check), castling rook-presence checks, and JDK 17 compatibility.

---

## 2. Included Open-Source Libraries in APK

All runtime libraries packaged in the application are official AndroidX / JetBrains libraries distributed under **Apache License 2.0**:
1. **AndroidX Core, Activity & Lifecycle (`androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-runtime-ktx`, `androidx.lifecycle:lifecycle-viewmodel-compose`)** — Apache License 2.0
2. **AndroidX Jetpack Compose UI, Graphics & Material 3 (`androidx.compose.ui:ui`, `androidx.compose.ui:ui-graphics`, `androidx.compose.material3:material3`)** — Apache License 2.0
3. **Kotlin Standard Library & Coroutines (`org.jetbrains.kotlin:kotlin-stdlib`, `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `org.jetbrains.kotlinx:kotlinx-coroutines-core`)** — Apache License 2.0
4. **JUnit 4, AndroidX Compose UI Test & Robolectric (Test-only scope)** — Eclipse Public License 1.0 / Apache License 2.0 / MIT License
