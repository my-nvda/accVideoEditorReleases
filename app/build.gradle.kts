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
        versionCode = 112
        versionName = "2.9.4"
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
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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

    lint {
        disable += setOf(
            "MissingTranslation"
        )
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0")

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
  
  // FFmpeg for Media Processing
  implementation(libs.ffmpeg.kit.full)

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
