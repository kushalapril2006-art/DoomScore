import java.util.Properties
import java.net.URI
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load) }
val backendUrl = local.getProperty("supabase.url") ?: System.getenv("DOOMSCORE_SUPABASE_URL") ?: ""
val publicKey = local.getProperty("supabase.key") ?: System.getenv("DOOMSCORE_SUPABASE_PUBLISHABLE_KEY") ?: ""
val captchaUrl = local.getProperty("captcha.url") ?: System.getenv("DOOMSCORE_CAPTCHA_URL") ?: ""
val developerName = local.getProperty("release.developerName") ?: ""
val supportEmail = local.getProperty("release.supportEmail") ?: ""
val privacyUrl = local.getProperty("release.privacyUrl") ?: ""
val deletionUrl = local.getProperty("release.deletionUrl") ?: ""
val leagueOnline = local.getProperty("league.backendVerified", "false").toBooleanStrict()
val leagueSafetyVerified = local.getProperty("league.safetyVerified", "false").toBooleanStrict()
val battleRelease = local.getProperty("release.battles", "false").toBooleanStrict()
// Firebase client configuration is public, but stays local to avoid coupling forks to our project.
val firebaseConfigFile = file("google-services.json")
val firebaseConfig = if(firebaseConfigFile.exists()) JsonSlurper().parse(firebaseConfigFile) as Map<*,*> else emptyMap<Any,Any>()
val firebaseProject = (firebaseConfig["project_info"] as? Map<*,*>)?.get("project_id") as? String ?: ""
val firebaseClient = (firebaseConfig["client"] as? List<*>)?.mapNotNull {it as? Map<*,*>}?.firstOrNull {
    ((it["client_info"] as? Map<*,*>)?.get("android_client_info") as? Map<*,*>)?.get("package_name")=="com.gridcc.doomscore.android"
}
val firebaseKey = ((firebaseClient?.get("api_key") as? List<*>)?.firstOrNull() as? Map<*,*>)?.get("current_key") as? String ?: ""
val googleClient = (firebaseClient?.get("oauth_client") as? List<*>)?.mapNotNull {it as? Map<*,*>}?.firstOrNull {(it["client_type"] as? Number)?.toInt()==3}?.get("client_id") as? String ?: ""
require(!firebaseConfigFile.exists() || firebaseClient!=null) {"Firebase configuration must register com.gridcc.doomscore.android"}
require(firebaseProject.isEmpty() || Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]").matches(firebaseProject)) {"Invalid Firebase project ID"}
require(firebaseKey.isEmpty() || Regex("[A-Za-z0-9_-]{30,100}").matches(firebaseKey)) {"Invalid Firebase client API key"}
require(googleClient.isEmpty() || Regex("[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com").matches(googleClient)) {"Invalid Google web client ID"}
val backendVerified = local.getProperty("release.backendVerified", "false").toBooleanStrict()
val battleSafetyVerified = local.getProperty("release.battleSafetyVerified", "false").toBooleanStrict()
val termsUrl = local.getProperty("release.termsUrl").orEmpty()
val signingPath = local.getProperty("release.signing.storeFile").orEmpty()
val signingAlias = local.getProperty("release.signing.keyAlias").orEmpty()
val signingStorePassword = System.getenv("DOOMSCORE_UPLOAD_STORE_PASSWORD").orEmpty()
val signingKeyPassword = System.getenv("DOOMSCORE_UPLOAD_KEY_PASSWORD").orEmpty()
val signingReady = signingPath.isNotBlank() && signingAlias.isNotBlank() && signingStorePassword.isNotBlank() && signingKeyPassword.isNotBlank()
require(publicKey.isEmpty() || Regex("sb_publishable_[A-Za-z0-9_-]{16,128}").matches(publicKey)) { "Only a Supabase publishable key may be included in the app" }
if (backendUrl.isNotEmpty()) {
    val url = runCatching { URI(backendUrl) }.getOrElse { throw GradleException("Supabase URL must be an HTTPS origin") }
    require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.fragment == null && url.path.orEmpty() in setOf("", "/") && (url.port == -1 || url.port == 443)) { "Supabase URL must be an HTTPS origin" }
}
if (captchaUrl.isNotEmpty()) {
    val url = runCatching { URI(captchaUrl) }.getOrElse { throw GradleException("CAPTCHA URL must use HTTPS") }
    require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && url.query == null && url.fragment == null && (url.port == -1 || url.port == 443)) { "CAPTCHA URL must use HTTPS" }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
fun publicHttps(value: String): Boolean = runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null && it.fragment == null && (it.port == -1 || it.port == 443) } }.getOrDefault(false)
val verifyReleaseConfiguration = tasks.register("verifyReleaseConfiguration") {
    group = "verification"
    description = "Reject production releases missing public legal details, signing, or enabled battle prerequisites."
    doLast {
        val missing = mutableListOf<String>()
        if(developerName.isBlank() || developerName.length > 100 || developerName.any { it.isISOControl() }) missing += "release.developerName"
        if(!Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matches(supportEmail)) missing += "release.supportEmail"
        if(!publicHttps(privacyUrl)) missing += "release.privacyUrl (public HTTPS policy)"
        if(!signingReady || !rootProject.file(signingPath).isFile) missing += "upload signing key and password environment variables"
        if(battleRelease && (backendUrl.isBlank() || publicKey.isBlank() || captchaUrl.isBlank() || !backendVerified || !battleSafetyVerified || !publicHttps(deletionUrl) || !publicHttps(termsUrl))) missing += "enabled battles: tested backend/CAPTCHA, deletion and terms URLs, reporting/blocking and moderation review"
        if(firebaseProject.isNotBlank() && (googleClient.isBlank() || !publicHttps(deletionUrl) || !publicHttps(termsUrl) || !local.getProperty("firebase.backendVerified", "false").toBooleanStrict() || !local.getProperty("firebase.abuseProtectionVerified", "false").toBooleanStrict())) missing += "Firebase: verified Google/guest sign-in, deployed rules/indexes, terms/deletion URLs, moderation and abuse protection"
        if(leagueOnline && (backendUrl.isBlank() || publicKey.isBlank() || captchaUrl.isBlank() || !publicHttps(deletionUrl) || !publicHttps(termsUrl) || !leagueSafetyVerified)) missing += "online league: deployed migration/CAPTCHA, terms/deletion URLs and moderation verification"
        if(missing.isNotEmpty()) throw GradleException("Production release blocked. Configure: " + missing.joinToString("; "))
        logger.lifecycle("Production configuration is complete. Store approval and physical-device acceptance remain separate requirements.")
    }
}
android {
    namespace = "com.gridcc.doomscore.android"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.gridcc.doomscore.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.5.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quoted(backendUrl))
        buildConfigField("String", "SUPABASE_KEY", quoted(publicKey))
        buildConfigField("String", "CAPTCHA_URL", quoted(captchaUrl))
        buildConfigField("boolean", "BATTLES_ENABLED", battleRelease.toString())
        buildConfigField("boolean", "LEAGUE_ONLINE_ENABLED", leagueOnline.toString())
        buildConfigField("String", "DEVELOPER_NAME", quoted(developerName))
        buildConfigField("String", "SUPPORT_EMAIL", quoted(supportEmail))
        buildConfigField("String", "PRIVACY_URL", quoted(privacyUrl))
        buildConfigField("String", "DELETION_URL", quoted(deletionUrl))
        buildConfigField("String", "TERMS_URL", quoted(termsUrl))
        buildConfigField("String", "FIREBASE_PROJECT", quoted(firebaseProject))
        buildConfigField("String", "FIREBASE_KEY", quoted(firebaseKey))
        buildConfigField("String", "GOOGLE_CLIENT_ID", quoted(googleClient))
    }
    buildFeatures { compose = true; buildConfig = true; resValues = true }
    sourceSets.getByName("androidTest").assets.srcDir("../backend/web")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    if(signingReady) signingConfigs.create("upload") {
        storeFile = rootProject.file(signingPath)
        storePassword = signingStorePassword
        keyAlias = signingAlias
        keyPassword = signingKeyPassword
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if(signingReady) signingConfig = signingConfigs.getByName("upload")
            buildConfigField("boolean", "BATTLES_ENABLED", battleRelease.toString())
            if(!battleRelease && !leagueOnline) {
                buildConfigField("String", "SUPABASE_URL", quoted(""))
                buildConfigField("String", "SUPABASE_KEY", quoted(""))
                buildConfigField("String", "CAPTCHA_URL", quoted(""))
            }
        }
    }
    lint { abortOnError = true }
}
tasks.configureEach { if(name == "preReleaseBuild") dependsOn(verifyReleaseConfiguration) }
dependencies {
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui:1.9.2")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.2")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
