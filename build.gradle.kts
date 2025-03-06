plugins {
    java
    `java-library`
    `maven-publish`
}

group = "io.github.prcraftmc"
version = "1.0-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    api("org.ow2.asm:asm:9.7.1")
    api("org.ow2.asm:asm-tree:9.7.1")

    compileOnly("org.jetbrains:annotations:24.0.1")

    api("com.nothome:javaxdelta:2.0.1")
    api("io.github.java-diff-utils:java-diff-utils:4.12")

//    implementation("net.lenni0451:Reflect:1.2.4")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.ow2.asm:asm-util:9.5")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(8)
    }
}

tasks.compileTestJava {
    options.encoding = "UTF-8"
    sourceCompatibility = "17"
    targetCompatibility = "17"
    if (JavaVersion.current().isJava9Compatible) {
        options.release.set(17)
    }
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "gaming32"
            credentials(PasswordCredentials::class)

            val baseUri = "https://maven.jemnetworks.com"
            url = uri(baseUri + if (version.toString().endsWith("-SNAPSHOT")) "/snapshots" else "/releases")
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
        }
    }
}
