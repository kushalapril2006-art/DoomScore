plugins { id("com.android.application") }
android {
    namespace = "com.gridcc.doomscore.fixture"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "com.instagram.android"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "TEST-ONLY" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
