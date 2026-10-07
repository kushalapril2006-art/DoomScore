buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        // Kotlin 2.4 requires R8 >= 9.1.29; AGP 9.0.1's bundled R8 predates it.
        classpath("com.android.tools:r8:9.1.56")
    }
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "org.bouncycastle" -> useVersion("1.86")
                requested.group == "org.apache.commons" && requested.name == "commons-lang3" -> useVersion("3.18.0")
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> useVersion("0.9.6")
                requested.group == "org.jdom" && requested.name == "jdom2" -> useVersion("2.0.6.1")
            }
        }
    }
}
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
