import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.accessiblevideoeditor"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.accessiblevideoeditor"
        minSdk = 29
        targetSdk = 36
        versionCode = 117
        versionName = "26.0"
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream -> localProperties.load(stream) }
    }

    signingConfigs {
        create("release") {
            val path = localProperties.getProperty("keystore.path")
            val password = localProperties.getProperty("keystore.password")
            val alias = localProperties.getProperty("keystore.alias")
            val aliasPassword = localProperties.getProperty("keystore.alias_password")

            if (path != null && password != null && alias != null && aliasPassword != null) {
                storeFile = file(path)
                storePassword = password
                keyAlias = alias
                keyPassword = aliasPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig?.storeFile != null) {
                signingConfig = releaseConfig
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += setOf(
            "MissingTranslation"
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
      aidl = false
      buildConfig = true
      shaders = false
      viewBinding = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.11.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")

  // Navigation
  implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
  implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  
  // FFmpeg for Media Processing
  implementation(libs.ffmpeg.kit.full)
  implementation(libs.smart.exception)

  // Local DeepFilterNet3 AI Noise Reduction
  implementation("io.github.kaleyravideo:android-deepfilternet:0.0.8")
  
  // ExoPlayer for Video Preview
  val media3_version = "1.3.1"
  implementation("androidx.media3:media3-exoplayer:$media3_version")
  implementation("androidx.media3:media3-ui:$media3_version")
  implementation("androidx.media3:media3-session:$media3_version")
  



}









tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions { freeCompilerArgs.add("-Xencoding=utf8") } }
