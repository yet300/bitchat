import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.local.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

kotlin {


    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.security.crypto)
            implementation(libs.bcprov.jdk15on)
        }
        commonMain.dependencies {
            implementation(projects.core.domain)

            implementation(libs.koin.core)
            api(libs.koin.annotation)

            implementation(libs.okio)

        }
        jvmMain.dependencies {
            implementation(libs.bcprov.jdk15on)
        }
        appleMain.dependencies {

        }
    }

    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}
dependencies {
    add("kspCommonMainMetadata", libs.koin.annotation.compiler)
    add("kspAndroid", libs.koin.annotation.compiler)
    add("kspIosX64", libs.koin.annotation.compiler)
    add("kspIosArm64", libs.koin.annotation.compiler)
    add("kspIosSimulatorArm64", libs.koin.annotation.compiler)
}

project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

ksp {
    arg("KOIN_CONFIG_CHECK","true")
}