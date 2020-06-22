import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.CommitVersionDescription
import me.qoomon.gradle.gitversioning.GitVersioningPluginConfig.VersionDescription
import java.util.Calendar
import java.net.URI

plugins {
    java
    jacoco
    maven
    `maven-publish`
    id("com.github.hierynomus.license") version "0.15.0"
    id("io.franzbecker.gradle-lombok") version "3.2.0"
    id("me.qoomon.git-versioning") version "2.1.1"
    id("com.github.ben-manes.versions") version "0.27.0"
}

group = "com.github.1c-syntax"

repositories {
    mavenCentral()
    maven { url = URI("https://jitpack.io") }
}

val junitVersion = "5.6.0"

dependencies {

    compileOnly("org.projectlombok", "lombok", lombok.version)

    implementation("com.github.hub4j:github-api:github-api-1.114")
    implementation("org.slf4j", "slf4j-api", "1.8.0-beta4")
    implementation("org.slf4j", "slf4j-simple", "1.8.0-beta4")

    // https://mvnrepository.com/artifact/commons-io/commons-io
    implementation("commons-io", "commons-io", "2.7")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    testImplementation("org.assertj", "assertj-core", "3.14.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }

    reports {
        html.isEnabled = true
    }
}


tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        xml.destination = File("$buildDir/reports/jacoco/test/jacoco.xml")
    }
}

license {
    header = rootProject.file("license/HEADER.txt")
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com>"
    ext["project"] = "1c-syntax utils"
    strictCheck = true
    mapping("java", "SLASHSTAR_STYLE")
    exclude("**/*.properties")
    exclude("**/*.xml")
    exclude("**/*.json")
    exclude("**/*.bsl")
}

gitVersioning.apply(closureOf<GitVersioningPluginConfig> {
    preferTags = true
    branch(closureOf<VersionDescription> {
        pattern = "^(?!v[0-9]+).*"
        versionFormat = "\${branch}-\${commit.short}\${dirty}"
    })
    tag(closureOf<VersionDescription>{
        pattern = "v(?<tagVersion>[0-9].*)"
        versionFormat = "\${tagVersion}\${dirty}"
    })
    commit(closureOf<CommitVersionDescription>{
        versionFormat = "\${commit.short}\${dirty}"
    })
})

lombok {
    version = "1.18.10"
    sha256 = "2836e954823bfcbad45e78c18896e3d01058e6f643749810c608b7005ee7b2fa"
}