import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

// InnerTube API key: NEVER hardcode in source. Precedence: environment
// YOUTUBE_INNERTUBE_API_KEY > -PinnertubeApiKey > local.properties
// innertube.apiKey (git-ignored) > "" (degraded: direct resolve fails
// honest, Piped fallback still attempted). CI injects via secret.
val innertubeApiKey: String =
  System.getenv("YOUTUBE_INNERTUBE_API_KEY")?.takeIf { it.isNotBlank() }?.trim()
    ?: (findProperty("innertubeApiKey") as? String)?.takeIf { it.isNotBlank() }?.trim()
    ?: run {
      val localFile = rootProject.file("local.properties")
      if (localFile.exists()) {
        val props = Properties()
        localFile.inputStream().use { props.load(it) }
        (props.getProperty("innertube.apiKey").orEmpty()).trim().takeIf { it.isNotBlank() }
      } else {
        null
      }
    }
    ?: ""

android {
  namespace = "app.rayik.music"
  // Mirror mpvium baseline: compile 37 (Compose/OkHttp requirement), target 36, min 26.
  compileSdk = 37

  defaultConfig {
    applicationId = "app.rayik.music"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.0.1-preview.2"

    vectorDrawables {
      useSupportLibrary = true
    }

    buildConfigField("String", "GIT_SHA", "\"${getCommitSha()}\"")
    buildConfigField("int", "GIT_COUNT", getCommitCount())
    // InnerTube API key: NEVER hardcode in source. See innertubeApiKey above.
    // CI injects via secret; local dev via env / -P / local.properties.
    buildConfigField("String", "INNERTUBE_API_KEY", "\"$innertubeApiKey\"")
  }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  buildTypes {
    named("release") {
      if (providers.gradleProperty("ciDebugSigning").isPresent) {
        signingConfig = signingConfigs.getByName("debug")
      }
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }

    named("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-${getCommitCount()}"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      pickFirsts += "/META-INF/{AL2.0,LGPL2.1}"
      pickFirsts += "META-INF/LICENSE*"
      pickFirsts += "META-INF/NOTICE*"
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    }
  }

  // Per-app language support (generateLocaleConfig) stays off until the
  // first translation lands — enabling it with no locales configured breaks
  // the build (extractDebugSupportedLocales: no resources.properties).
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xwhen-guards",
      "-Xcontext-parameters",
      "-Xannotation-default-target=param-property",
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    )
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

composeCompiler {
  includeSourceInformation = true
}

room {
  schemaDirectory("$projectDir/schemas")
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.material3.android)
  implementation(libs.google.material)
  implementation(libs.androidx.ui.tooling.preview)
  debugImplementation(libs.androidx.ui.tooling)
  implementation(libs.bundles.compose.navigation3)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.material3.icons.extended)
  implementation(libs.saveable)
  implementation(libs.palette.ktx)

  // Streaming-first player: Media3 ExoPlayer (NOT libmpv — video-first, no gapless/EQ/session).
  implementation(libs.bundles.media3.playback)
  implementation(libs.media3.effect)

  implementation(platform(libs.koin.bom))
  implementation(libs.bundles.koin)

  implementation(libs.room.runtime)
  ksp(libs.room.compiler)
  implementation(libs.room.ktx)

  implementation(libs.kotlinx.immutable.collections)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  testImplementation(libs.junit)
}

/* ---------------- Git helpers ---------------- */

fun getCommitCount(): String =
  runCommand("git rev-list --count HEAD") ?: "0"

fun getCommitSha(): String =
  runCommand("git rev-parse --short HEAD") ?: "unknown"

fun runCommand(command: String): String? =
  try {
    val parts = command.split(' ')
    val process = ProcessBuilder(parts)
      .redirectErrorStream(true)
      .start()

    val output = process.inputStream
      .bufferedReader()
      .readText()
      .trim()

    process.waitFor()
    output.ifEmpty { null }
  } catch (e: Exception) {
    null
  }
