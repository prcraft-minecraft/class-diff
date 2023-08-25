plugins {
    application
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.prcraftmc"
version = "1.0-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

application {
    mainClass.set("io.github.prcraftmc.classdiff.cli.ClassDiffCli")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation("net.sourceforge.argparse4j:argparse4j:0.9.0")
    implementation("org.ow2.asm:asm-util:9.5")
    implementation("org.jline:jline-terminal-jansi:3.23.0")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    mergeServiceFiles()
}

tasks.compileJava {
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    }
}
