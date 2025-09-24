import java.util.Calendar
import org.jreleaser.model.Active.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("org.cadixdev.licenser") version "0.6.1"
    id("me.qoomon.git-versioning") version "6.4.4"
    id("io.freefair.lombok") version "8.14.2"
    id("io.freefair.javadoc-links") version "8.14.2"
    id("io.freefair.javadoc-utf-8") version "8.14.2"
    id("io.freefair.maven-central.validate-poms") version "8.14.2"
    id("com.github.ben-manes.versions") version "0.53.0"
    id("ru.vyarus.pom") version "3.0.0"
    id("org.jreleaser") version "1.19.0"
}

group = "io.github.1c-syntax"
gitVersioning.apply {
    refs {
        describeTagFirstParent = false
        tag("v(?<tagVersion>[0-9].*)") {
            version = "\${ref.tagVersion}\${dirty}"
        }

        branch("develop") {
            version = "\${describe.tag.version.major}." +
                    "\${describe.tag.version.minor.next}.0." +
                    "\${describe.distance}-SNAPSHOT\${dirty}"
        }

        branch(".+") {
            version = "\${ref}-\${commit.short}\${dirty}"
        }
    }

    rev {
        version = "\${commit.short}\${dirty}"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.github.spotbugs", "spotbugs-annotations", "4.8.6")
    testImplementation("org.junit.jupiter", "junit-jupiter-api", "5.11.4")
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", "5.11.4")
    testImplementation("org.assertj", "assertj-core", "3.27.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
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
        html.required.set(true)
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/test/jacoco.xml"))
    }
}

license {
    header(rootProject.file("license/HEADER.txt"))
    newLine(false)
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com>"
    ext["project"] = "1c-syntax utils"
    exclude("**/*.properties")
    exclude("**/*.xml")
    exclude("**/*.json")
    exclude("**/*.bsl")
}

tasks.javadoc {
    options {
        this as StandardJavadocDocletOptions
        noComment(false)
    }
}

artifacts {
    archives(tasks["jar"])
    archives(tasks["sourcesJar"])
    archives(tasks["javadocJar"])
}

publishing {
    repositories {
        maven {
            name = "staging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                description.set("Common utils for 1c-syntax team java projects")
                url.set("https://github.com/1c-syntax/utils")
                licenses {
                    license {
                        name.set("GNU LGPL 3")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("asosnoviy")
                        name.set("Alexey Sosnoviy")
                        email.set("labotamy@gmail.com")
                        url.set("https://github.com/asosnoviy")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("nixel2007")
                        name.set("Nikita Fedkin")
                        email.set("nixel2007@gmail.com")
                        url.set("https://github.com/nixel2007")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                    developer {
                        id.set("theshadowco")
                        name.set("Valery Maximov")
                        email.set("maximovvalery@gmail.com")
                        url.set("https://github.com/theshadowco")
                        organization.set("1c-syntax")
                        organizationUrl.set("https://github.com/1c-syntax")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/1c-syntax/utils.git")
                    developerConnection.set("scm:git:git@github.com:1c-syntax/utils.git")
                    url.set("https://github.com/1c-syntax/utils")
                }
                // Добавлено для Maven Central validation
                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/1c-syntax/utils/issues")
                }
                // Добавлено для Maven Central validation
                ciManagement {
                    system.set("GitHub Actions")
                    url.set("https://github.com/1c-syntax/utils/actions")
                }
            }
        }
    }
}

jreleaser {
    signing {
        active = ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("release-deploy") {
                    active = RELEASE
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
            nexus2 {
                create("snapshot-deploy") {
                    active = SNAPSHOT
                    snapshotUrl = "https://central.sonatype.com/repository/maven-snapshots/"
                    applyMavenCentralRules = true
                    snapshotSupported = true
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}
