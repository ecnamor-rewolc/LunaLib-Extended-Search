plugins {
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenCentral()
}

val gameDir = findProperty("starsectorDir")?.toString() ?: "C:/Program Files (x86)/Fractal Softworks/Starsector"
val modDir  = "${projectDir}"

dependencies {
    // Starsector API — compile-only, provided at runtime by the game
    compileOnly(files(
        "$gameDir/starsector-core/starfarer.api.jar",
        "$gameDir/starsector-core/starfarer_obf.jar",
        "$gameDir/starsector-core/fs.common_obf.jar",
        "$gameDir/starsector-core/lwjgl.jar",
        "$gameDir/starsector-core/lwjgl_util.jar",
        "$gameDir/starsector-core/log4j-1.2.9.jar",
        "$gameDir/starsector-core/json.jar",
        "$gameDir/mods/LazyLib-3.0.0/jars/LazyLib.jar",
        "$modDir/jars/libs/fuzzywuzzy-1.3.0.jar"
    ))
}

// Target Java 1.8 bytecode without requiring JDK 8 to be installed.
// Kotlin 2.x can emit 1.8 bytecode from a JDK 26 compiler.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

// Source sets: both Kotlin and Java under src/
sourceSets {
    main {
        java.srcDir("src")
        kotlin.srcDir("src")
    }
}

// Output jar goes to jars/LunaLib.jar (overwrites existing)
tasks.jar {
    archiveFileName.set("LunaLib.jar")
    destinationDirectory.set(file("jars"))
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
