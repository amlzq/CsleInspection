import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.amlzq.csle.inspection"
        description = markdownToHTML(file("pluginDescription.md").readText())
        changeNotes = markdownToHTML(file("changeNotes.md").readText())
        ideaVersion {
            sinceBuild = "253"
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

dependencies {
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea("2025.3.6.1") // Target IDE Platform
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("com.intellij.modules.xml")
        bundledPlugin("HtmlTools")
        bundledPlugin("com.intellij.css")
        compatiblePlugin("PythonCore")
        compatiblePlugin("com.jetbrains.php")
        compatiblePlugin("Dart")
    }
    implementation(libs.opencc4j)
}

kotlin {
    jvmToolchain(21)
}
