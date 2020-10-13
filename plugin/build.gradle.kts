import java.nio.file.*

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "0.12.0"
    id("org.jetbrains.dokka") version "0.10.1"

    jacoco

    maven
    id("com.mobilesolutionworks.gradle.publish") version "2.0.1"
}

publication {
    name = "plugin"
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

jacoco {
    toolVersion = "0.8.6"
    reportsDir = file("$buildDir/reports")
}

group = "com.mobilesolutionworks.gradle"
version = "1.0.0"

gradlePlugin {
    plugins {
        create("works-enteprise-repo") {
            id = "com.mobilesolutionworks.gradle.enterprise-repository"
            displayName = "Works Enterprise Repository Plugin"
            description = "Enterprise Repository Plugin"

            implementationClass = "com.mobilesolutionworks.gradle.enterprise.RepositoryPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/yunarta/works-enteprise-repo-gradle-plugin"
    vcsUrl = "https://github.com/yunarta/works-enteprise-repo-gradle-plugin.git"
    tags = listOf("works", "enteprise", "corporate", "maven", "nexus", "artifactory")
}

//val jacocoAnt by configurations.creating
val jacocoRuntime by configurations.creating

dependencies {
    jacocoRuntime("org.jacoco:org.jacoco.agent:0.8.6")

    testImplementation("junit", "junit", "4.12")
    testImplementation(gradleTestKit())

    afterEvaluate {
        testRuntimeOnly(files(tasks.findByName("createClasspathManifest")))
        testRuntimeOnly(files(tasks.findByName("setupJacocoAgent")))
    }

    testImplementation("org.mockito:mockito-core:3.5.13")
    testImplementation("org.spockframework:spock-core:1.3-groovy-2.4") {
        exclude(group = "org.codehaus.groovy")
    }

    api(gradleApi())

    compileOnly("org.jetbrains.dokka:dokka-gradle-plugin:0.10.1")
    compileOnly("com.android.tools.build:gradle:3.1.2") {
        isTransitive = false
    }
    compileOnly("com.android.tools.build:builder-model:3.1.2") {
        isTransitive = false
    }
    compileOnly("com.android.tools.build:gradle-core:3.1.2") {
        isTransitive = false
    }
}

task("cleanTest", Delete::class) {
    delete(
        tasks.getByName("test").outputs.files,
        Paths.get("build", "tmp", "runTest").toFile()
    )
}

tasks.create("createClasspathManifest") {
    group = "plugin development"
    description = "Create classpath manifest required to be used in GradleRunner"

    val outputDir = file("$buildDir/$name")

    doFirst {
        outputDir.mkdirs()
    }

    doLast {
        File(outputDir, "plugin-classpath.txt").apply {
            writeText(sourceSets["main"].runtimeClasspath.joinToString(System.lineSeparator()))
        }
        File(outputDir, "plugin-under-test-metadata.properties").apply {
            writeText(
                "implementation-classpath=" +
                        sourceSets["main"].runtimeClasspath.joinToString(":")
            )
        }
    }

    inputs.files(sourceSets.getAt("main").runtimeClasspath)
    outputs.dir(outputDir)
}

tasks.create("jacocoCoverageTest", JacocoReport::class.java) {
    group = "Reporting"
    description = "Generate Jacoco coverage reports for Debug build"

//    dependsOn("setupJacocoAgent", "test")
    val dataPaths = fileTree(mapOf("dir" to project.rootDir.absolutePath, "include" to "**/build/jacoco/*.exec"))
    dataPaths.map {
        println("it = ${it}")
    }
    inputs.files(dataPaths)
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }

    // generated classes
    additionalClassDirs(
        fileTree(
            mapOf(
                "dir" to "$buildDir/classes/java/main"
            )
        )
    )

    // sources
    additionalSourceDirs(files(listOf("src/main/java", "/src/test/groovy")))
    executionData(fileTree(mapOf("dir" to project.rootDir.absolutePath, "include" to "**/build/jacoco/*.exec")))
}

tasks.create("automationTest") {
    group = "automation"
    description = "Execute test with coverage"

    dependsOn("cleanTest", "jacocoCoverageTest")
}

tasks.create("automationCheck") {
    group = "automation"
    description = "Execute check"

    dependsOn("detektCheck")
}

tasks.create("copyJacoco", Copy::class.java) {
    group = "jacoco"
    description = "Unzip jacocoagent to be used as javaagent in Gradle Runner"

    val outputDir = file("$buildDir/jacocoJar")

    doFirst {
        outputDir.mkdirs()
    }

    from(configurations["jacocoRuntime"].asPath)
    into(outputDir)
}

tasks.create("unzipJacoco", Copy::class.java) {
    group = "jacoco"
    description = "Unzip jacocoagent to be used as javaagent in Gradle Runner"

    val outputDir = file("$buildDir/jacocoAgent")

    doFirst {
        outputDir.mkdirs()
    }

    from(zipTree(configurations["jacocoRuntime"].asPath))
    into(outputDir)
}

tasks.create("setupJacocoAgent") {
    group = "jacoco"
    description = "Write gradle.properties file to be used in Gradle Runner"

    dependsOn("unzipJacoco")

    val outputDir = file("$buildDir/jacocoAgent")
    doFirst {
        outputDir.mkdirs()
        file("$outputDir/gradle.properties").writeText("")
    }

    doLast {
        val jacocoPath = File(outputDir, "jacocoagent.jar").absolutePath

        val gradleProperties = file("$outputDir/gradle.properties")
        if (gradle.taskGraph.hasTask(":${project.name}:jacocoCoverageTest")) {
            val jacocoOutputDir = File(buildDir, "jacoco").absolutePath
            gradleProperties.writeText("""org.gradle.jvmargs=-javaagent:${jacocoPath}=destfile=$jacocoOutputDir""".trimMargin())

            logger.quiet(
                """Gradle properties for Tests
                   |${gradleProperties.readText()}
            """.trimMargin()
            )
        }
    }

    outputs.dir(outputDir)
}

val ignoreFailures: String? by rootProject.extra
val shouldIgnoreFailures = ignoreFailures?.toBoolean() ?: false

tasks.withType<Test> {
    dependsOn("cleanTest", "createClasspathManifest")

    maxParallelForks = Runtime.getRuntime().availableProcessors().div(2)
    ignoreFailures = shouldIgnoreFailures

    doFirst {
        logger.quiet("Test with max $maxParallelForks parallel forks")
    }
}