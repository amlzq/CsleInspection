import org.jetbrains.changelog.markdownToHTML

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    // https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "2.2.1"
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        id = "com.amlzq.csle.inspection"
        group = "com.amlzq"
        version = "0.3.0"
        description = markdownToHTML(file("pluginDescription.md").readText())
        changeNotes = markdownToHTML(file("changeNotes.md").readText())
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    signing {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }
    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.4") // Target IDE Platform
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        plugins(
            listOf(
                "Dart:243.23654.44",
                "JavaScript:243.25659.59",
                "com.jetbrains.php:243.25659.59",
                "PythonCore:243.24978.46",
            )
        )
    }
    implementation("com.github.houbb:opencc4j:1.8.1")
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
}
