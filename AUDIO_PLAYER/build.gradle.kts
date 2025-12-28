plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id ("com.google.devtools.ksp")
}

// JitPack-style coords
group = "com.github.MehulKD"
version = "1.0.4" // this should match your Git tag later

android {
    namespace = "com.saregama.android.audioplayer"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    ksp (libs.androidx.room.compiler)
    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.smoothstreaming)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.media)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)

    // SQLCipher
//    implementation(libs.android.database.sqlcipher)
}

// JitPack only needs publishing -> mavenLocal, no remote repo block needed
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = project.group.toString()    // com.github.MehulKD
                artifactId = "AUDIO_PLAYER"           // repo name
                version = project.version.toString()  // 1.0.0

                pom {
                    name.set("Saregama AudioPlayer")
                    description.set("Headless Media3-based audio player with playlists, encrypted DB, downloads, notifications, etc.")
                }
            }
        }
    }
}
