plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

group = "io.github.ibabyblue"
version = "0.2.0"

android {
    namespace = "com.ibabyblue.aparticle"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "aparticle"
                pom {
                    name = "AParticle"
                    description = "Native particle animations for Jetpack Compose and Android Views."
                    url = "https://github.com/ibabyblue/AParticle"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/licenses/MIT"
                            distribution = "repo"
                        }
                    }
                    developers {
                        developer {
                            id = "ibabyblue"
                            name = "ibabyblue"
                            email = "ibabyblue_z@icloud.com"
                        }
                    }
                    scm {
                        connection = "scm:git:git://github.com/ibabyblue/AParticle.git"
                        developerConnection = "scm:git:ssh://github.com/ibabyblue/AParticle.git"
                        url = "https://github.com/ibabyblue/AParticle"
                    }
                }
            }
        }
    }
}
