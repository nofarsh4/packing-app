plugins { id("com.android.application") }

android {
    namespace = "com.nofar.packing"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nofar.packing"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    // A fixed key kept in the (private) repo so every build installs over the previous one.
    signingConfigs {
        create("fixed") {
            storeFile = file("../keystore.jks")
            storePassword = "packing123"
            keyAlias = "packing"
            keyPassword = "packing123"
        }
    }
    buildTypes { debug { signingConfig = signingConfigs.getByName("fixed") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation("androidx.core:core:1.13.1") }
