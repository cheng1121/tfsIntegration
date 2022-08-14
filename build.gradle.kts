plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.8.0"
}

group = "org.jetbrains"
version = "222"

sourceSets {
    main {

        java{
            srcDirs("src")
        }
        resources{
            srcDirs("resources")
        }
    }
    test {
        java{
            srcDirs("tests")
        }
        resources{
            srcDir("testData")
        }
    }
}

repositories {
    mavenCentral()
    flatDir{
        dirs("lib")
    }
}

dependencies {

    implementation(fileTree("lib"){include("*.jar")})
    testImplementation("junit:junit:4.13.2")
    testImplementation(fileTree("test-lib"){include("*.jar")})
}

intellij {
    version.set("2022.2")
    type.set("IC")
}

tasks{
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("213")
        untilBuild.set("223.*")
    }

//    prepareSandbox {
//        from('lib/native') {
//            into "${intellij.pluginName}/lib/native"
//        }
//    }
    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}