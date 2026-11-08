import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.secrets)
}

android {
    namespace = "app.shilex.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.shilex.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Phones only (ONNX Runtime ships native code per CPU type).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Release signing configuration.
    val releaseKeystore = System.getenv("SHILEX_KEYSTORE")
    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SHILEX_KEYSTORE_PASSWORD")
                keyAlias = "jarvis"
                keyPassword = System.getenv("SHILEX_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
            ndk { abiFilters += listOf("x86_64") }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // SpeexDSP's echo canceller (BSD), built from src/main/cpp.
    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    // Android calls such as Log do nothing in local unit tests instead of throwing.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    // On-device wake word (openWakeWord ONNX models), MIT licence.
    implementation(libs.onnxruntime.android)
    // Offline speech for local commands (Vosk, Apache 2.0); the model comes from fetchVoskModel.
    implementation(libs.vosk.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // Android's own org.json is a stub in local unit tests.
    testImplementation(libs.org.json)
}

// Vosk's small Indian-English model (Apache 2.0, 36 MB zip): too big for git, so it is
// downloaded once at build time and checked against a pinned SHA-256.
val voskModelName = "vosk-model-small-en-in-0.4"
val voskModelSha256 = "20663dcac4d5cb783a579c54d98339344a688e4ec6e1b4a4b059fd1235454cc7"
val voskAssets = layout.buildDirectory.dir("generated/vosk-assets")

abstract class FetchVoskModelTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
    private val archiveOperations: ArchiveOperations,
) : DefaultTask() {

    @get:Input
    abstract val modelName: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val out = outputDir.get().asFile
        if (out.resolve("vosk/am/final.mdl").exists()) return

        val mName = modelName.get()
        val expectedHash = expectedSha256.get()
        val tempDir = temporaryDir
        val zip = tempDir.resolve("$mName.zip")

        val uri = URI("https://alphacephei.com/vosk/models/$mName.zip")
        val connection = uri.toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        val inputStream: java.io.InputStream = connection.getInputStream()
        inputStream.use { input ->
            zip.outputStream().use { outStream ->
                input.copyTo(outStream)
            }
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(zip.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == expectedHash) { "Vosk model download doesn't match its pinned hash ($digest)" }

        fileSystemOperations.copy {
            from(archiveOperations.zipTree(zip))
            into(out)
            exclude("**/README")
            eachFile { path = path.replaceFirst(mName, "vosk") }
            includeEmptyDirs = false
        }
    }
}

val fetchVoskModel by tasks.registering(FetchVoskModelTask::class) {
    modelName.set(voskModelName)
    expectedSha256.set(voskModelSha256)
    outputDir.set(voskAssets)
}

android.sourceSets["main"].assets.srcDir(voskAssets)
tasks.named("preBuild") { dependsOn(fetchVoskModel) }

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}
