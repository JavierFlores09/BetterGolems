plugins {
    alias(libs.plugins.java)
    alias(libs.plugins.paperweight)
    alias(libs.plugins.resource.factory)
    alias(libs.plugins.run.paper)
}

group = property("group")!!
version = property("version")!!

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION


val versionJvm = property("java_version").toString().toInt()
val vendorJvm = JvmVendorSpec.matching(property("java_vendor").toString())

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle(libs.versions.dev.bundle)
}

paperPluginYaml {
    main = "$group.${project.name.lowercase()}.${project.name}"
    authors.add("JavierFlores09")
    apiVersion = "1.21.9"
}

tasks.runDevBundleServer {
    val jvmArgsFile = project.file("jvm.args")

    if (jvmArgsFile.exists()) {
        val argsFromFile = jvmArgsFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        jvmArgs(argsFromFile)
    }
    debugOptions {
        enabled = true
        server = true
        suspend = false
        host = "*"
        port = 5005
    }
}

java {
    toolchain {
        vendor = vendorJvm
        languageVersion = JavaLanguageVersion.of(versionJvm)

    }
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        vendor = vendorJvm
        languageVersion = JavaLanguageVersion.of(versionJvm)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = versionJvm

}

// This isn't really necessary but for sake of completeness
tasks.updateDaemonJvm {
    vendor = vendorJvm
    languageVersion = JavaLanguageVersion.of(versionJvm)
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}
