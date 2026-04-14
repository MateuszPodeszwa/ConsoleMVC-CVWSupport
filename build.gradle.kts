import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        rider(providers.gradleProperty("platformVersion"))
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = """
            Provides IDE support for ConsoleMVC .cvw (Console View) files in JetBrains Rider.
            Features syntax highlighting, code completion, and navigation for the ConsoleMVC framework.
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// --- ReSharper Backend Build Integration ---

val dotnetDir = projectDir.resolve("src/dotnet")
val dotnetSolution = dotnetDir.resolve("CvwSupport.sln")
val buildConfiguration = "Debug"

val buildBackend by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the ReSharper backend plugin"

    inputs.files(fileTree(dotnetDir) {
        include("**/*.cs", "**/*.csproj", "**/*.sln", "**/*.props")
    })

    commandLine("dotnet", "build", dotnetSolution.absolutePath,
        "-c", buildConfiguration,
        "--nologo", "-v", "minimal")
    workingDir = dotnetDir
}

tasks.withType<PrepareSandboxTask> {
    dependsOn(buildBackend)

    val outputDir = dotnetDir.resolve("bin/CvwSupport/$buildConfiguration/net472")

    from(outputDir) {
        into("${intellijPlatform.projectName.get()}/dotnet")
        include("CvwSupport.dll")
        include("CvwSupport.pdb")
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
