import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
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
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = """
            <p>Support for <a href="https://github.com/MateuszPodeszwa/ConsoleMVC">ConsoleMVC</a> <code>.cvw</code> views in JetBrains Rider: C-View Markup and the C# around it.</p>

            <h3>Features</h3>
            <ul>
                <li><b>Highlighting</b>: tags, attributes, values, text, <code>@</code> expressions, code blocks, directives and comments</li>
                <li><b>Completion</b> from ConsoleMVC's markup schema: the elements allowed where you type, attributes, values, closing tags and directives</li>
                <li><b>Quick documentation</b> for every element, attribute and directive</li>
                <li><b>Checks while typing</b>: the build's own diagnostics through <code>cvw-preview</code>, or checks against the schema</li>
                <li><b>Live preview</b>: the ConsoleMVC Preview tool window draws the view with ConsoleMVC's rendering engine (<code>dotnet tool install -g ConsoleMVC.Preview</code>)</li>
                <li><b>Navigation</b> between views and controllers, model type mismatch checks</li>
                <li><b>ReSharper backend</b>: the view's C# is analysed, completed and navigated through a design-time document</li>
                <li><b>Live and file templates</b> for views, forms and tables</li>
            </ul>
        """.trimIndent()

        changeNotes = """
            <h3>0.2.0</h3>
            <ul>
                <li>C-View Markup: highlighting, completion, quick documentation and checks, from ConsoleMVC's markup schema</li>
                <li>Checks with the build's own diagnostics when the cvw-preview tool is installed</li>
                <li>The ConsoleMVC Preview tool window, and Settings | Tools | ConsoleMVC</li>
                <li>The backend's design-time document leaves markup out and checks the expressions in it, with the generator's namespace, base class and model</li>
                <li>Comments use @* *@; templates write markup views; a view without @model is no longer flagged</li>
            </ul>
            <h3>0.1.0</h3>
            <ul>
                <li>Initial release for ConsoleMVC 1.x views (directives and C#)</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

    // The Plugin Verifier checks every IDE API the plugin uses against the Rider it targets
    pluginVerification {
        ides {
            create(IntelliJPlatformType.Rider, providers.gradleProperty("platformVersion"))
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
