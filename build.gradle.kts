import java.util.Calendar
import org.jreleaser.model.Active.*

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("cloud.rio.license") version "0.18.0"
    id("me.qoomon.git-versioning") version "6.4.4"
    id("io.freefair.lombok") version "9.2.0"
    id("io.freefair.javadoc-links") version "9.2.0"
    id("io.freefair.javadoc-utf-8") version "9.2.0"
//    id("io.freefair.maven-central.validate-poms") version "9.2.0"
    id("com.github.ben-manes.versions") version "0.53.0"
    id("ru.vyarus.pom") version "3.0.0"
    id("org.jreleaser") version "1.21.0"
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
    api("org.jspecify:jspecify:1.0.0")

    testImplementation("org.assertj:assertj-core:3.27.7")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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
    header = rootProject.file("license/HEADER.txt")
    skipExistingHeaders = false
    strictCheck = true
    mapping("java", "SLASHSTAR_STYLE")
    ext["year"] = "2018-" + Calendar.getInstance().get(Calendar.YEAR)
    ext["name"] = "Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com>"
    ext["project"] = "1c-syntax utils"
    include("**/*.java")
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
