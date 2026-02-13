buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("com.guardsquare:proguard-gradle:7.8.2") }
}

plugins {
    kotlin("jvm") version "2.3.20-Beta2"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.ju1"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("org.apache.logging.log4j:log4j-core:2.25.3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks {
    runServer {
        downloadPlugins {
            modrinth("valhallammo", "1.8")
        }
        minecraftVersion("1.21.8")
    }
}

val targetJavaVersion = 21

kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.shadowJar {
//    minimize()

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/maven/**")
    exclude("META-INF/*.kotlin_module")
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.withType<Jar> {
    includeEmptyDirs = false
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.register<proguard.gradle.ProGuardTask>("optimize") {
    dependsOn("shadowJar")

    val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
    injars(shadowJarFile)
    outjars(shadowJarFile.parentFile.resolve("${shadowJarFile.nameWithoutExtension}-obf.jar"))

    libraryjars(configurations.compileClasspath.get() - configurations.runtimeClasspath.get())

    libraryjars("${System.getProperty("java.home")}/jmods")

    keep("class com.ju1.fishDetector.FishDetector { <init>(); }")

    keepclassmembers("""
        class * {
            @org.bukkit.event.EventHandler *;
        }
    """.trimIndent())

    keepattributes("RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable")
    repackageclasses("a")

    dontwarn("org.bukkit.**")
    dontwarn("io.papermc.**")
    dontwarn("com.destroystokyo.paper.**")
    dontwarn("org.apache.logging.log4j.**")
    dontwarn("net.kyori.**")
    dontwarn("javax.annotation.**")
    dontwarn("org.checkerframework.**")
    dontwarn("org.jetbrains.annotations.**")
    dontwarn("com.google.gson.**")
    dontwarn("com.google.common.**")
    dontwarn("it.unimi.dsi.fastutil.**")

    assumenosideeffects("""
        class kotlin.jvm.internal.Intrinsics {
            static void checkNotNullParameter(...);
            static void checkExpressionValueIsNotNull(...);
            static void checkNotNull(...);
        }
    """.trimIndent())

    dontwarn("sun.misc.Unsafe")
    dontwarn("java.lang.invoke.**")
    dontwarn("kotlin.reflect.**")

    optimizationpasses(5)
}