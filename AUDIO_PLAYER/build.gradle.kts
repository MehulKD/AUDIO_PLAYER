plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish") // ⬅️ added
}

// Maven coordinates
group = "com.saregama.android"   // groupId
version = "1.0.0"                // bump this for new releases

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

    // ⬇️ tell AGP which variant to publish
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

    //Media3
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
    // SQLCipher (Zetetic)
    implementation(libs.android.database.sqlcipher)
}

// ⬇️ publishing config for GitHub Packages
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("gpr") {
                from(components["release"])

                groupId = project.group.toString()    // com.saregama.android
                artifactId = "audioplayer"            // dependency name
                version = project.version.toString()  // 1.0.0

                pom {
                    name.set("Saregama AudioPlayer")
                    description.set("Headless Media3-based audio player with playlists, encrypted DB, downloads, notifications, etc.")
                    url.set("https://github.com/MehulKD/AUDIO_PLAYER")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("MehulKD")
                            name.set("Mehul Kadam")
                            email.set("you@example.com") // change if you like
                        }
                    }
                    scm {
                        url.set("https://github.com/MehulKD/AUDIO_PLAYER")
                        connection.set("scm:git:https://github.com/MehulKD/AUDIO_PLAYER.git")
                        developerConnection.set("scm:git:ssh://git@github.com/MehulKD/AUDIO_PLAYER.git")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/MehulKD/AUDIO_PLAYER")

                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                        ?: (findProperty("gpr.user") as String?)
                    password = System.getenv("GITHUB_TOKEN")
                        ?: (findProperty("gpr.key") as String?)
                }
            }
        }
    }
}
