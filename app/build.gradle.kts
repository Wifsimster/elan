import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signature de release. La clé d'upload vient soit d'un `keystore.properties`
// local non versionné (postes de dev — voir keystore.properties.example), soit
// des variables d'environnement (CI). Ni le keystore ni ses mots de passe ne
// sont jamais commités. Sans clé d'upload configurée on retombe sur la clé
// debug pour que les builds locaux et CI restent installables — mais un AAB
// destiné au Play Store DOIT être construit avec la vraie clé d'upload.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
    (keystoreProps.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

val releaseStoreFile: String? = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning: Boolean = releaseStoreFile != null

// Source unique de vérité pour la version de l'app. `VERSION_NAME` vit dans
// gradle.properties et est incrémenté automatiquement par semantic-release à
// chaque release. versionCode dérive du semver, donc croît toujours.
val appVersionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "0.0.0"
// Bandes de 1000 (minor et patch chacun < 1000) : un cycle mineur long peut
// aller jusqu'au patch 999, et le mineur jusqu'à 999, sans jamais entrer en
// collision ni régresser sous le composant suivant — Play refuse un
// versionCode qui ne croît pas. 2.0.0 -> 2_000_000.
val appVersionCode: Int = appVersionName.substringBefore("-").split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "ovh.battistella.elan"
    compileSdk = 36

    defaultConfig {
        applicationId = "ovh.battistella.elan"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Clé d'upload dédiée si configurée (keystore.properties ou env
            // CI) ; sinon repli sur la clé debug pour que les builds locaux et
            // CI produisent toujours un artefact installable.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Les API Material 3 Expressive (motion scheme, wavy progress, loading
        // indicators) sont encore annotées expérimentales.
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
    }
    androidResources {
        // Génère locales_config.xml depuis les dossiers values-* pour que les
        // langues apparaissent dans le sélecteur par app d'Android 13+. Les
        // ressources par défaut (non qualifiées) sont en français — déclaré
        // dans res/resources.properties.
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        // Les schémas Room exportés sont mis sur le classpath des tests
        // unitaires (AGP ne fusionne pas src/test/assets) pour qu'un
        // MigrationTest puisse ouvrir une base à une version antérieure et
        // exécuter la vraie migration. Les tests instrumentés lisent les mêmes
        // fichiers depuis les assets, où MigrationTestHelper les attend.
        getByName("test").resources.srcDir(layout.projectDirectory.dir("schemas"))
        getByName("androidTest").assets.srcDir(layout.projectDirectory.dir("schemas"))
    }

    testOptions {
        unitTests {
            // Les tests Robolectric + Compose ont besoin des ressources et du
            // manifeste Android fusionnés au runtime JVM (hôte).
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Exporte un schéma JSON par version de base dans app/schemas. Versionnés, ils
// servent de référence aux tests de migration.
ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Icônes Material provisoires de la barre de navigation (Home, DateRange,
    // Settings) ; remplacées par les icônes MDI vectorielles au jalon des assets.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Activées par les jalons qui en ont besoin (carte, Health Connect, QR).
    implementation(libs.maplibre)
    implementation(libs.androidx.health.connect)
    implementation(libs.play.code.scanner)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.okhttp.mockwebserver)
}
