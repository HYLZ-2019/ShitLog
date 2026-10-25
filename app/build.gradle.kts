plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val signingEnvironment = listOf(
    "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
).associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingEnvironment.values.all { !it.isNullOrBlank() }
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        val missing = signingEnvironment.filterValues { it.isNullOrBlank() }.keys
        check(missing.isEmpty()) { "Fixed release signing is required. Missing: ${missing.joinToString()}" }
        check(file(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!).isFile) { "Release keystore not found" }
    }
}

android {
    namespace = "com.lunhui.log"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.lunhui.log"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("fixedRelease") {
                storeType = "JKS"
                storeFile = file(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!)
                storePassword = signingEnvironment.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("ANDROID_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("fixedRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Stable pinned test parser; Android uses platform org.json.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
