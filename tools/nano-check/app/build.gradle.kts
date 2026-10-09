plugins { id("com.android.application") }

android {
    namespace = "com.famiglia.tripcompanion.nanocheck"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.famiglia.tripcompanion.nanocheck"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // This is a diagnostic, not a planner or a production build of Trip Companion.
    buildTypes.getByName("debug") { isDebuggable = false }
}

dependencies {
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
