// AGP 9 は Kotlin を内蔵しているため、org.jetbrains.kotlin.android プラグインは適用しない。
// Kotlin のバージョンは、下の buildscript で KGP を指定して固定する
// （gradle/libs.versions.toml の kotlin と同じ値にそろえること）。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
