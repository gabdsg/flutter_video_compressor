import com.android.build.gradle.LibraryExtension

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            lint {
                // These checks fail inside example-only third-party plugin
                // sources. Keep every other lint check enabled.
                when (project.name) {
                    "gal" -> disable += "GradlePluginVersion"
                    "flutter_native_video_trimmer" ->
                        disable += "UnsafeOptInUsageError"
                    "get_thumbnail_video" -> disable += "NewApi"
                }
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
