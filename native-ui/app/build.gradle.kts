plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val legacyRoot = rootProject.projectDir.parentFile
val legacyManifest = legacyRoot.resolve("AndroidManifest.xml").readText()
val generatedManifest = layout.buildDirectory.file("generated/legacy/AndroidManifest.xml")
val prepareManifest by tasks.registering {
    inputs.file(legacyRoot.resolve("AndroidManifest.xml")); inputs.file(project.file("build.gradle.kts")); outputs.file(generatedManifest)
    doLast {
        val extra = """<activity android:name=".ComposeInboxActivity" android:exported="false" android:launchMode="singleTop" />
          <activity-alias android:name=".ComposePilotLauncher" android:targetActivity=".ComposeInboxActivity" android:exported="true" android:label="Почта · Compose" android:icon="@mipmap/mail_launcher">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
          </activity-alias>
          <activity-alias android:name=".ComposeClassicLauncher" android:targetActivity=".ComposeInboxActivity" android:enabled="false" android:exported="true" android:label="Почта · Compose" android:icon="@mipmap/classic_launcher">
            <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
          </activity-alias>"""
        generatedManifest.get().asFile.apply { parentFile.mkdirs(); writeText(legacyManifest
            .replace(" package=\"ru.local.spbumailpilot\"", "")
            .replace(Regex("<uses-sdk[^>]*/>"), "")
            .replace(" android:debuggable=\"false\"", "")
            .replace("</application>", extra + "</application>")) }
    }
}
android {
    namespace = "ru.local.spbumailpilot"
    compileSdk = 34
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "ru.local.spbumailpilot"
        minSdk = 26; targetSdk = 34
        versionCode = providers.gradleProperty("nativeVersionCode").orNull?.toInt() ?: Regex("versionCode=\"(\\d+)\"").find(legacyManifest)!!.groupValues[1].toInt()
        versionName = providers.gradleProperty("nativeVersionName").orNull ?: (Regex("versionName=\"([^\"]+)\"").find(legacyManifest)!!.groupValues[1] + if (providers.gradleProperty("candidateRelease").isPresent) "" else "-native-dev")
    }
    sourceSets["main"].apply {
        java.srcDir(legacyRoot.resolve("src")); res.srcDir(legacyRoot.resolve("res"))
        assets.srcDir(legacyRoot.resolve("assets")); manifest.srcFile(generatedManifest)
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
    buildFeatures { compose = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) } }
tasks.named("preBuild") { dependsOn(prepareManifest) }
dependencies {
    implementation(fileTree(legacyRoot.resolve("lib")) { include("*.jar"); exclude("json-test*", "kotlin-*", "annotations-*") })
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
}
// Optional host-rendered previews: production APK dependencies and entry points are unchanged.
if (providers.gradleProperty("renderUi").isPresent) {
    apply(plugin = "app.cash.paparazzi")
    android.sourceSets["test"].java.srcDir("src/previewTest/kotlin")
    dependencies.add("testImplementation", "junit:junit:4.13.2")
    android.testOptions.unitTests.isReturnDefaultValues = true
}
