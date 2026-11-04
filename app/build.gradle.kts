plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.chessbook.chone"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  // implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  // implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  // implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  // debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

abstract class ValidateContentPacksTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.InputDirectory
  abstract val assetsDirectory: org.gradle.api.file.DirectoryProperty

  @org.gradle.api.tasks.TaskAction
  fun executeValidation() {
    val assetsDir = assetsDirectory.get().asFile
    val booksDir = File(assetsDir, "books")
    if (!booksDir.exists()) {
      throw org.gradle.api.GradleException("[validateContentPacks] Missing directory: app/src/main/assets/books")
    }

    val slurper = groovy.json.JsonSlurper()
    fun parseJsonFile(target: File): Any {
      if (!target.exists()) {
        throw org.gradle.api.GradleException("[validateContentPacks] File not found: ${target.relativeTo(assetsDir)}")
      }
      return try {
        slurper.parseText(target.readText(Charsets.UTF_8))
      } catch (e: Exception) {
        throw org.gradle.api.GradleException("[validateContentPacks] Invalid JSON syntax in ${target.relativeTo(assetsDir)}: ${e.message}")
      }
    }

    fun validateFenSyntax(fen: String, fileLabel: String, objectId: String) {
      val parts = fen.trim().split(Regex("\\s+"))
      if (parts.size != 6) {
        throw org.gradle.api.GradleException("[validateContentPacks] [$fileLabel] [$objectId] Invalid FEN (expected 6 fields): '$fen'")
      }
      val ranks = parts[0].split("/")
      if (ranks.size != 8) {
        throw org.gradle.api.GradleException("[validateContentPacks] [$fileLabel] [$objectId] Invalid FEN board ranks (expected 8 ranks): '$fen'")
      }
      for ((rIdx, rankStr) in ranks.withIndex()) {
        var count = 0
        for (ch in rankStr) {
          when {
            ch in '1'..'8' -> count += (ch - '0')
            ch in "prnbqkPRNBQK" -> count += 1
            else -> throw org.gradle.api.GradleException("[validateContentPacks] [$fileLabel] [$objectId] Invalid FEN character '$ch' in rank ${8 - rIdx}")
          }
        }
        if (count != 8) {
          throw org.gradle.api.GradleException("[validateContentPacks] [$fileLabel] [$objectId] FEN rank ${8 - rIdx} has $count squares instead of 8: '$fen'")
        }
      }
      if (parts[1] != "w" && parts[1] != "b") {
        throw org.gradle.api.GradleException("[validateContentPacks] [$fileLabel] [$objectId] Invalid FEN side to move '${parts[1]}'")
      }
    }

    // 1. Check all JSON files inside assets/books/ for valid syntax
    booksDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { jsonFile ->
      parseJsonFile(jsonFile)
    }

    // 2. Validate library.json
    val libraryFile = File(booksDir, "library.json")
    val libraryMap = parseJsonFile(libraryFile) as? Map<*, *>
      ?: throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] Root must be a JSON object")
    val booksList = libraryMap["books"] as? List<*>
      ?: throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] Missing 'books' array")
    if (booksList.isEmpty()) {
      throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] 'books' array cannot be empty")
    }

    val seenBookIds = mutableSetOf<String>()
    for (bookObj in booksList) {
      val book = bookObj as? Map<*, *>
        ?: throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] Invalid book entry")
      val bookId = book["bookId"]?.toString()?.trim().orEmpty()
      if (bookId.isEmpty()) {
        throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] Book entry missing 'bookId'")
      }
      if (!seenBookIds.add(bookId)) {
        throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Duplicate bookId")
      }

      val frontMatterObj = book["frontMatter"] as? Map<*, *>
      if (frontMatterObj != null) {
        val fmEnabled = frontMatterObj["enabled"] as? Boolean ?: true
        if (fmEnabled) {
          val fmRegionId = frontMatterObj["contentRegionId"]?.toString()?.trim().orEmpty()
          if (fmRegionId != "FRONT_MATTER") {
            throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Invalid frontMatter contentRegionId '$fmRegionId'")
          }
          val fmManifestPath = frontMatterObj["manifestPath"]?.toString()?.trim().orEmpty()
          if (fmManifestPath.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Missing frontMatter.manifestPath")
          }
          val fmManifestFile = File(assetsDir, fmManifestPath)
          val bookFolder = File(booksDir, bookId)
          val fmManifestMap = parseJsonFile(fmManifestFile) as? Map<*, *>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [$fmManifestPath] Root must be a JSON object")
          val fmPagesList = fmManifestMap["sourcePdfPages"] as? List<*>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [$fmManifestPath] Missing 'sourcePdfPages'")
          if (fmPagesList.size != 39) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$fmManifestPath] Expected 39 sourcePdfPages, found ${fmPagesList.size}")
          }
          val seenFmBlockIds = mutableSetOf<String>()
          for ((idx, pEntry) in fmPagesList.withIndex()) {
            val expectedPdfPage = idx + 1
            val pMap = pEntry as? Map<*, *>
              ?: throw org.gradle.api.GradleException("[validateContentPacks] [$fmManifestPath] Invalid entry at index $idx")
            val entryPdfPage = (pMap["sourcePdfPage"] as? Number)?.toInt() ?: -1
            if (entryPdfPage != expectedPdfPage) {
              throw org.gradle.api.GradleException("[validateContentPacks] [$fmManifestPath] Expected sourcePdfPage $expectedPdfPage at index $idx, found $entryPdfPage")
            }
            val pageRelFile = pMap["file"]?.toString()?.trim().orEmpty()
            val pageFile = File(bookFolder, pageRelFile)
            val pageJson = parseJsonFile(pageFile) as? Map<*, *>
              ?: throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] Root must be a JSON object")
            val filePdfPage = (pageJson["sourcePdfPage"] as? Number)?.toInt() ?: -1
            if (filePdfPage != expectedPdfPage) {
              throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] sourcePdfPage mismatch: expected $expectedPdfPage, found $filePdfPage")
            }
            val blocksList = pageJson["orderedContentBlocks"] as? List<*>
              ?: throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] Missing orderedContentBlocks")
            for (bEntry in blocksList) {
              val bMap = bEntry as? Map<*, *> ?: continue
              val bId = bMap["blockId"]?.toString()?.trim().orEmpty()
              if (bId.isEmpty() || !seenFmBlockIds.add(bId)) {
                throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] Invalid or duplicate blockId '$bId'")
              }
              val bPdfPage = (bMap["sourcePdfPage"] as? Number)?.toInt() ?: -1
              if (bPdfPage != expectedPdfPage) {
                throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] [$bId] Block sourcePdfPage=$bPdfPage does not match page $expectedPdfPage")
              }
            }
            val assetsList = pageJson["sourceAssets"] as? List<*> ?: emptyList<Any>()
            for (aEntry in assetsList) {
              val aMap = aEntry as? Map<*, *> ?: continue
              val assetRel = aMap["file"]?.toString()?.trim().orEmpty()
              val assetFile = File(bookFolder, assetRel)
              if (!assetFile.exists()) {
                throw org.gradle.api.GradleException("[validateContentPacks] [${pageFile.relativeTo(assetsDir)}] Missing image asset file: ${assetFile.relativeTo(assetsDir)}")
              }
            }
          }
          val linksRel = fmManifestMap["sourceLinksFile"]?.toString()?.trim().orEmpty()
          val linksFile = File(bookFolder, linksRel)
          val linksMap = parseJsonFile(linksFile) as? Map<*, *>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [${linksFile.relativeTo(assetsDir)}] Invalid JSON")
          val linksArr = linksMap["links"] as? List<*> ?: emptyList<Any>()
          if (linksArr.size != 126) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${linksFile.relativeTo(assetsDir)}] Expected 126 source links, found ${linksArr.size}")
          }
        }
      }

      val chaptersList = book["chapters"] as? List<*>
        ?: throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Missing 'chapters' array")
      val seenChapterIds = mutableSetOf<String>()

      for (chObj in chaptersList) {
        val ch = chObj as? Map<*, *>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Invalid chapter entry")
        val chapterId = ch["chapterId"]?.toString()?.trim().orEmpty()
        if (chapterId.isEmpty()) {
          throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$bookId] Chapter entry missing 'chapterId'")
        }
        if (!seenChapterIds.add(chapterId)) {
          throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$chapterId] Duplicate chapterId in book '$bookId'")
        }
        val enabled = ch["enabled"] as? Boolean ?: true
        val manifestPath = ch["manifestPath"]?.toString()?.trim().orEmpty()
        if (manifestPath.isEmpty()) {
          throw org.gradle.api.GradleException("[validateContentPacks] [books/library.json] [$chapterId] Missing 'manifestPath'")
        }

        if (!enabled) continue

        val chapterManifestFile = File(assetsDir, manifestPath)
        val chapterFolder = chapterManifestFile.parentFile
        val chapterRel = chapterManifestFile.relativeTo(assetsDir).path
        val chapterMap = parseJsonFile(chapterManifestFile) as? Map<*, *>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$chapterId] Root must be a JSON object")

        val manifestChapterId = chapterMap["chapterId"]?.toString()?.trim().orEmpty()
        if (manifestChapterId != chapterId) {
          throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$chapterId] chapterId mismatch: expected '$chapterId', found '$manifestChapterId'")
        }

        val diagramsFileRel = chapterMap["diagramsFile"]?.toString()?.trim().orEmpty()
        val pgnFileRel = (chapterMap["pgnSequencesFile"] ?: chapterMap["pgnFile"])?.toString()?.trim().orEmpty()
        val exercisesFileRel = chapterMap["exercisesFile"]?.toString()?.trim().orEmpty()
        val narrationFileRel = (chapterMap["narrationManifestFile"] ?: chapterMap["narrationManifest"])?.toString()?.trim().orEmpty()

        val diagramsFile = File(chapterFolder, diagramsFileRel)
        val pgnFile = File(chapterFolder, pgnFileRel)
        val exercisesFile = File(chapterFolder, exercisesFileRel)
        val narrationFile = File(chapterFolder, narrationFileRel)

        // Validate diagrams.json
        val diagramsRoot = parseJsonFile(diagramsFile)
        val diagramsList = when (diagramsRoot) {
          is List<*> -> diagramsRoot
          is Map<*, *> -> diagramsRoot["diagrams"] as? List<*>
          else -> null
        } ?: throw org.gradle.api.GradleException("[validateContentPacks] [${diagramsFile.relativeTo(assetsDir)}] [$chapterId] Missing diagrams list")

        val seenDiagramIds = mutableSetOf<String>()
        val diagramPgnRefs = mutableMapOf<String, String>()
        for (dObj in diagramsList) {
          val d = dObj as? Map<*, *> ?: continue
          val dId = (d["diagram_id"] ?: d["diagramId"] ?: d["id"])?.toString()?.trim().orEmpty()
          if (dId.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${diagramsFile.relativeTo(assetsDir)}] [$chapterId] Diagram missing 'diagram_id'")
          }
          if (!seenDiagramIds.add(dId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${diagramsFile.relativeTo(assetsDir)}] [$dId] Duplicate diagramId")
          }
          val fen = (d["fen"] ?: d["initialFen"])?.toString()?.trim().orEmpty()
          validateFenSyntax(fen, diagramsFile.relativeTo(assetsDir).path, dId)
          val pgnRef = (d["pgn_ref"] ?: d["pgnRef"])?.toString()?.trim().orEmpty()
          if (pgnRef.isNotEmpty() && pgnRef != "null") {
            diagramPgnRefs[dId] = pgnRef
          }
        }

        // Validate pgn_sequences.json
        val pgnRoot = parseJsonFile(pgnFile)
        val pgnList: List<*> = when (pgnRoot) {
          is List<*> -> pgnRoot
          is Map<*, *> -> {
            val seqArr = pgnRoot["sequences"] as? List<*>
            seqArr ?: pgnRoot.entries.mapNotNull { entry ->
              val v = entry.value as? Map<*, *>
              if (v != null && (v.containsKey("moves") || v.containsKey("fens") || v.containsKey("startFen") || v.containsKey("start_fen"))) {
                v
              } else null
            }
          }
          else -> emptyList<Any>()
        }
        if (pgnList.isEmpty()) {
          throw org.gradle.api.GradleException("[validateContentPacks] [${pgnFile.relativeTo(assetsDir)}] [$chapterId] Missing PGN sequences list")
        }

        val seenPgnRefs = mutableSetOf<String>()
        for (pObj in pgnList) {
          val p = pObj as? Map<*, *> ?: continue
          val pId = (p["id"] ?: p["pgnRef"] ?: p["sequenceId"])?.toString()?.trim().orEmpty()
          if (pId.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${pgnFile.relativeTo(assetsDir)}] [$chapterId] PGN sequence missing 'id'")
          }
          if (!seenPgnRefs.add(pId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${pgnFile.relativeTo(assetsDir)}] [$pId] Duplicate pgnRef")
          }
          val fensList = p["fens"] as? List<*>
          val startFen = (p["start_fen"] ?: p["startFen"] ?: fensList?.firstOrNull())?.toString()?.trim().orEmpty()
          validateFenSyntax(startFen, pgnFile.relativeTo(assetsDir).path, pId)
        }

        // Check every diagram pgn_ref exists in pgn_sequences.json
        for ((dId, pRef) in diagramPgnRefs) {
          if (!seenPgnRefs.contains(pRef)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${diagramsFile.relativeTo(assetsDir)}] [$dId] References unknown pgn_ref '$pRef'")
          }
        }

        // Validate exercises.json
        val exercisesRoot = parseJsonFile(exercisesFile) as? Map<*, *>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$chapterId] Root must be JSON object")
        val exercisesList = exercisesRoot["exercises"] as? List<*> ?: emptyList<Any>()
        val seenExerciseIds = mutableMapOf<String, String>() // exerciseId -> diagramId
        for (exObj in exercisesList) {
          val ex = exObj as? Map<*, *> ?: continue
          val exId = ex["exerciseId"]?.toString()?.trim().orEmpty()
          if (exId.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$chapterId] Exercise missing 'exerciseId'")
          }
          if (seenExerciseIds.containsKey(exId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$exId] Duplicate exerciseId")
          }
          val exDiagramId = ex["diagramId"]?.toString()?.trim().orEmpty()
          if (!seenDiagramIds.contains(exDiagramId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$exId] References unknown diagramId '$exDiagramId'")
          }
          val exFen = ex["initialFen"]?.toString()?.trim().orEmpty()
          validateFenSyntax(exFen, exercisesFile.relativeTo(assetsDir).path, exId)
          val solutions = (ex["acceptedSolutionIds"] as? List<*>)?.map { it.toString().trim() }?.filter { it.isNotEmpty() }.orEmpty()
          if (solutions.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$exId] 'acceptedSolutionIds' cannot be empty")
          }
          for (solId in solutions) {
            if (!seenPgnRefs.contains(solId)) {
              throw org.gradle.api.GradleException("[validateContentPacks] [${exercisesFile.relativeTo(assetsDir)}] [$exId] Solution '$solId' not found in pgn_sequences.json")
            }
          }
          seenExerciseIds[exId] = exDiagramId
        }

        // Validate sections
        val sectionsManifestList = chapterMap["sections"] as? List<*>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$chapterId] Missing 'sections' array")
        if (sectionsManifestList.isEmpty()) {
          throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$chapterId] 'sections' array cannot be empty")
        }
        val seenSectionIds = mutableSetOf<String>()
        val seenBlockIds = mutableSetOf<String>()
        val seenBlockIdsBySection = mutableMapOf<String, MutableSet<String>>()

        for (secEntryObj in sectionsManifestList) {
          val secEntry = secEntryObj as? Map<*, *> ?: continue
          val secId = secEntry["sectionId"]?.toString()?.trim().orEmpty()
          val secFileRel = (secEntry["file"] ?: secEntry["contentFile"])?.toString()?.trim().orEmpty()
          if (secId.isEmpty() || secFileRel.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$chapterId] Section entry missing sectionId or file")
          }
          if (!seenSectionIds.add(secId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$chapterRel] [$secId] Duplicate sectionId")
          }
          val secBlockIds = seenBlockIdsBySection.getOrPut(secId) { mutableSetOf() }
          val secFile = File(chapterFolder, secFileRel)
          val secMap = parseJsonFile(secFile) as? Map<*, *>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$secId] Root must be JSON object")
          val fileSecId = secMap["sectionId"]?.toString()?.trim().orEmpty()
          if (fileSecId != secId) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$secId] sectionId mismatch: expected '$secId', found '$fileSecId'")
          }

          val blocks = (secMap["orderedContentBlocks"] ?: secMap["orderedBlocks"]) as? List<*>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$secId] Missing 'orderedContentBlocks' array")
          if (blocks.isEmpty()) {
            throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$secId] 'orderedContentBlocks' cannot be empty")
          }

          for (bObj in blocks) {
            val b = bObj as? Map<*, *> ?: continue
            val blockId = b["blockId"]?.toString()?.trim().orEmpty()
            val bType = b["type"]?.toString()?.trim().orEmpty()
            if (blockId.isEmpty()) {
              throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$secId] Block missing 'blockId'")
            }
            if (!seenBlockIds.add(blockId)) {
              throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$blockId] Duplicate blockId across chapter")
            }
            secBlockIds.add(blockId)
            when (bType.uppercase()) {
              "DIAGRAM" -> {
                val dId = b["diagramId"]?.toString()?.trim().orEmpty()
                if (!seenDiagramIds.contains(dId)) {
                  throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$blockId] DiagramBlock references unknown diagramId '$dId'")
                }
              }
              "EXERCISE" -> {
                val dId = b["diagramId"]?.toString()?.trim().orEmpty()
                val exId = b["exerciseId"]?.toString()?.trim().orEmpty()
                if (!seenDiagramIds.contains(dId)) {
                  throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$blockId] ExerciseBlock references unknown diagramId '$dId'")
                }
                if (!seenExerciseIds.containsKey(exId)) {
                  throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$blockId] ExerciseBlock references unknown exerciseId '$exId'")
                }
                if (seenExerciseIds[exId] != dId) {
                  throw org.gradle.api.GradleException("[validateContentPacks] [${secFile.relativeTo(assetsDir)}] [$blockId] ExerciseBlock diagramId '$dId' does not match exercise '$exId' diagramId '${seenExerciseIds[exId]}'")
                }
              }
            }
          }
        }

        // Validate narration/manifest.json strictly against the unified contract (chapters -> sections -> blocks)
        val narrationRel = narrationFile.relativeTo(assetsDir).path
        val narrationMap = parseJsonFile(narrationFile) as? Map<*, *>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Root must be JSON object")

        val allowedRootKeys = setOf("schemaVersion", "chapters")
        val allowedChapterKeys = setOf("chapterId", "sections")
        val allowedSectionKeys = setOf("sectionId", "blocks")
        val allowedBlockKeys = setOf("blockId", "audioAssetPath", "status", "durationMs")
        val allowedStatuses = setOf("AVAILABLE", "UNAVAILABLE")

        for (k in narrationMap.keys) {
          val keyStr = k?.toString().orEmpty()
          if (keyStr !in allowedRootKeys) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Unsupported root property '$keyStr' in narration manifest")
          }
        }

        val rawSchemaVer = narrationMap["schemaVersion"]
        val narSchemaVer = when (rawSchemaVer) {
          is Int -> rawSchemaVer
          is Long -> rawSchemaVer.toInt()
          else -> -1
        }
        if (!narrationMap.containsKey("schemaVersion") || narSchemaVer != 1) {
          throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Invalid schemaVersion '$rawSchemaVer' (expected integer 1)")
        }

        val validNarIdRegex = Regex("^[A-Za-z0-9_\\-]{1,128}$")
        val safeAudioPathRegex = Regex("^[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*\\.(mp3|wav|ogg|m4a|aac)$")
        val driveLetterRegex = Regex("^[A-Za-z]:.*")
        val allowedAudioExts = listOf(".mp3", ".wav", ".ogg", ".m4a", ".aac")

        fun isValidNarId(id: String?): Boolean =
          !id.isNullOrBlank() && !id.contains("__part_") && validNarIdRegex.matches(id)

        fun isValidAudioPath(rawPath: String?): Boolean {
          if (rawPath.isNullOrBlank()) return false
          val trimmed = rawPath.trim()
          if (trimmed != rawPath) return false
          if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains("\\")) return false
          if (driveLetterRegex.matches(trimmed)) return false
          val lower = trimmed.lowercase()
          if (lower.contains("://") || lower.startsWith("http:") || lower.startsWith("https:") ||
              lower.startsWith("file:") || lower.startsWith("content:") || lower.startsWith("asset:") ||
              lower.startsWith("data:") || lower.startsWith("javascript:")
          ) return false
          if (trimmed.contains("//")) return false
          val segs = trimmed.split("/")
          if (segs.isEmpty()) return false
          for (s in segs) {
            if (s.isEmpty() || s == "." || s == ".." || s.contains("..")) return false
          }
          val fileName = segs.last().lowercase()
          if (allowedAudioExts.none { fileName.endsWith(it) && fileName.length > it.length }) return false
          return safeAudioPathRegex.matches(trimmed)
        }

        fun isValidDurationMs(rawDur: Any?): Boolean {
          return when (rawDur) {
            is Int -> rawDur >= 0
            is Long -> rawDur in 0..Int.MAX_VALUE
            else -> false
          }
        }

        val narrationRootCanonical = File(assetsDir, "narration").canonicalFile

        fun resolveAudioFile(audioPath: String): File? {
          if (!isValidAudioPath(audioPath)) return null
          val candidate = File(assetsDir, "narration/$audioPath")
          val canonical = try {
            candidate.canonicalFile
          } catch (_: Exception) {
            return null
          }
          val rootPrefix = narrationRootCanonical.path + File.separator
          if (!canonical.path.startsWith(rootPrefix)) return null
          return canonical.takeIf { it.exists() && it.isFile }
        }

        val narChaptersList = narrationMap["chapters"] as? List<*>
          ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Narration manifest must contain a required 'chapters' array")

        val seenNarChapterIds = mutableSetOf<String>()
        for (chObj in narChaptersList) {
          val chMap = chObj as? Map<*, *>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Chapter entry in narration manifest must be a JSON object")

          for (k in chMap.keys) {
            val keyStr = k?.toString().orEmpty()
            if (keyStr !in allowedChapterKeys) {
              throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Unsupported property '$keyStr' in chapter entry")
            }
          }

          val narChId = chMap["chapterId"] as? String ?: ""
          if (!chMap.containsKey("chapterId") || !isValidNarId(narChId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Invalid or empty chapterId '$narChId' in narration manifest")
          }
          if (!seenNarChapterIds.add(narChId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] Duplicate chapterId in narration manifest")
          }

          val narSections = chMap["sections"] as? List<*>
            ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] Missing 'sections' array in narration manifest")
          val seenNarSections = mutableSetOf<String>()
          val seenNarBlocksInChapter = mutableSetOf<String>()

          for (secObj in narSections) {
            val secMapNar = secObj as? Map<*, *>
              ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] Section entry in narration manifest must be a JSON object")

            for (k in secMapNar.keys) {
              val keyStr = k?.toString().orEmpty()
              if (keyStr !in allowedSectionKeys) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] Unsupported property '$keyStr' in section entry")
              }
            }

            val narSecId = secMapNar["sectionId"] as? String ?: ""
            if (!secMapNar.containsKey("sectionId") || !isValidNarId(narSecId)) {
              throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] Invalid or empty sectionId '$narSecId' in narration manifest")
            }
            if (!seenNarSections.add(narSecId)) {
              throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] Duplicate sectionId in narration manifest")
            }
            if (narChId == chapterId && !seenSectionIds.contains(narSecId)) {
              throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] Section '$narSecId' in narration manifest does not match any real section of chapter '$chapterId'")
            }

            val narBlocks = secMapNar["blocks"] as? List<*>
              ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] Missing 'blocks' array in narration manifest")
            val seenNarBlocksInSection = mutableSetOf<String>()
            val validBlocksForSec = seenBlockIdsBySection[narSecId].orEmpty()

            for (blkObj in narBlocks) {
              val blkMap = blkObj as? Map<*, *>
                ?: throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] Block entry in narration manifest must be a JSON object")
              val narBlkId = blkMap["blockId"] as? String ?: ""

              for (k in blkMap.keys) {
                val keyStr = k?.toString().orEmpty()
                if (keyStr !in allowedBlockKeys) {
                  throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Unsupported property '$keyStr' in block entry")
                }
              }

              if (!blkMap.containsKey("blockId") || !isValidNarId(narBlkId)) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Invalid or empty blockId '$narBlkId' in narration manifest")
              }
              if (!seenNarBlocksInSection.add(narBlkId) || !seenNarBlocksInChapter.add(narBlkId)) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Duplicate blockId in narration manifest")
              }
              if (narChId == chapterId && !validBlocksForSec.contains(narBlkId)) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Block '$narBlkId' does not exist in section '$narSecId' of chapter '$chapterId'")
              }
              if (blkMap.containsKey("durationMs") && !isValidDurationMs(blkMap["durationMs"])) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Invalid durationMs '${blkMap["durationMs"]}'")
              }
              val audioAssetPath = blkMap["audioAssetPath"] as? String
              if (!blkMap.containsKey("audioAssetPath") || !isValidAudioPath(audioAssetPath)) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Invalid or missing audioAssetPath '$audioAssetPath'")
              }
              val status = blkMap["status"] as? String
              if (!blkMap.containsKey("status") || status == null || status !in allowedStatuses) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Invalid or missing status '$status' (expected AVAILABLE or UNAVAILABLE)")
              }
              if (status == "AVAILABLE" && resolveAudioFile(audioAssetPath!!) == null) {
                throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$narChId] [$narSecId] [$narBlkId] Missing audio file for AVAILABLE block: '$audioAssetPath'")
              }
            }
          }
        }

        if (seenNarChapterIds.isNotEmpty()) {
          if (seenNarChapterIds.size == 1 && seenNarChapterIds.first() != chapterId) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] chapterId mismatch in narration manifest: expected '$chapterId', found '${seenNarChapterIds.first()}'")
          }
          if (!seenNarChapterIds.contains(chapterId)) {
            throw org.gradle.api.GradleException("[validateContentPacks] [$narrationRel] [$chapterId] Expected chapterId '$chapterId' not found in narration manifest chapters: $seenNarChapterIds")
          }
        }
      }
    }
    println("[validateContentPacks] All book content packs in assets/books/ validated successfully.")
  }
}

tasks.register<ValidateContentPacksTask>("validateContentPacks") {
  group = "verification"
  description = "Validates all book content packs in app/src/main/assets/books/"
  assetsDirectory.set(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") {
  dependsOn("validateContentPacks")
}

