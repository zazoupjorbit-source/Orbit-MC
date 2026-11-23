plugins {
    `java-library`
}

val paperApi = "io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")

    group = rootProject.group
    version = rootProject.version

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        compileOnly(paperApi)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

// Collecte les jars finaux dans build/libs a la racine
val collectJars = tasks.register<Copy>("collectJars") {
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.named("jar"))
        from(sub.tasks.named("jar"))
    }
    into(layout.buildDirectory.dir("libs"))
}

tasks.register("buildAll") {
    dependsOn(collectJars)
}
