import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// La versión vive aquí y en ningún sitio más: `scripts/release.sh` la reescribe
// y el `versionCode` sale de ella, para que no puedan discrepar.
val muxyVersionName = "0.1.17"

/**
 * 0.1.10 -> 110. Multiplicar por 100 cada tramo deja sitio para 99 versiones de
 * parche; contar releases a mano acabaría repitiendo un número, y un versionCode
 * repetido hace que Android se niegue a actualizar sin decir por qué.
 */
val muxyVersionCode = muxyVersionName.split(".").let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

/**
 * Credenciales de firma. Están fuera del repositorio (el repo es público), así
 * que si no aparecen el build sigue funcionando: solo sale un APK de release sin
 * firmar, que sirve para compilar pero no para instalar.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.muxy.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.muxy.app"
        minSdk = 26
        targetSdk = 36
        versionCode = muxyVersionCode
        versionName = muxyVersionName

        // De dónde se bajan las actualizaciones. En el código no hay ninguna URL
        // suelta: si algún día cambia el repositorio, se cambia aquí.
        buildConfigField("String", "UPDATE_REPO", "\"Yoryito/Muxy\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf {
                keystoreProperties.isNotEmpty()
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
    implementation(libs.media3.common)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.newpipe.extractor)
    implementation(libs.ealvatag)

    // Solo tests de JVM: lo que tiene reglas de verdad (limpiar títulos de
    // YouTube, decidir qué entra en cada sección del inicio) es Kotlin puro y no
    // necesita dispositivo. Lo que sí lo necesitaría —Room, Media3, Compose— se
    // prueba a mano en el móvil, como el resto del proyecto.
    testImplementation(libs.junit)
}
