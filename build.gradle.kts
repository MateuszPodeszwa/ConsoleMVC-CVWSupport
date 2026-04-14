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
            <p>Provides first-class IDE support for <a href="https://github.com/MateuszPodeszwa/ConsoleMVC">ConsoleMVC</a> <code>.cvw</code> (Console View) files in JetBrains Rider.</p>

            <h3>Features</h3>
            <ul>
                <li><b>Syntax highlighting</b> — directives (<code>@model</code>, <code>@using</code>) and full C# code body highlighting</li>
                <li><b>Code completion</b> — directives, <code>NavigationResult</code> methods, <code>Console.*</code>, <code>Model</code>, <code>ViewData</code></li>
                <li><b>Error highlighting</b> — missing <code>@model</code>, empty arguments, missing return statement, model type mismatches</li>
                <li><b>Navigation</b> — gutter icons linking views to controllers, Go to Related, <code>NavigationResult.To()</code> target navigation</li>
                <li><b>Refactoring</b> — rename/move model classes propagates to <code>.cvw</code> files via ReSharper backend</li>
                <li><b>Live templates</b> — <code>cvw</code>, <code>navto</code>, <code>navaction</code>, <code>navquit</code> snippets</li>
                <li><b>File templates</b> — "New ConsoleMVC View" in the New menu</li>
                <li><b>Structure view</b> — shows <code>@model</code> type, imports, and navigation targets</li>
                <li><b>Code folding</b>, <b>brace matching</b>, <b>comment toggling</b>, <b>quick documentation</b></li>
                <li><b>Quick-fixes</b> — add missing <code>@model</code> directive, add return statement</li>
                <li><b>ReSharper backend</b> — full semantic C# analysis via generated document service</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>0.1.0</h3>
            <ul>
                <li>Initial release</li>
                <li>Full syntax highlighting for directives and C# code body</li>
                <li>Code completion for directives and framework types</li>
                <li>Error annotations and quick-fixes</li>
                <li>Bidirectional view/controller navigation</li>
                <li>Live templates and file templates</li>
                <li>Structure view, code folding, and color settings</li>
                <li>ReSharper backend with generated document service for semantic C# analysis</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
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
val buildConfiguration = "Release"

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

    val outputDir = dotnetDir.resolve("CvwSupport/bin/CvwSupport/$buildConfiguration/net472")

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
