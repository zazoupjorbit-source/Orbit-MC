dependencies {
    compileOnly(project(":api"))
}

tasks.jar {
    archiveBaseName.set("OrbitHCF-Combat")
}
