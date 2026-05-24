plugins {
    id("checkstyle")
    id("com.github.spotbugs") version "6.5.5"
    id("com.gradleup.shadow") version "9.3.1"
    id("java")
    id("eclipse")
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }

    maven {
        name = "enginehub"
        url = uri("https://maven.enginehub.org/repo/")
    }

    maven {
        name = "momirealms"
        url = uri("https://repo.momirealms.net/releases/")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }

    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.65-stable")
    compileOnly("com.google.code.gson:gson:2.13.2")
    compileOnly("net.momirealms:craft-engine-core:26.5.1")
    compileOnly("net.momirealms:craft-engine-bukkit:26.5.1")
    compileOnly("net.momirealms:craft-engine-adventure:26.5.1")
    compileOnly(files("../server/plugins/FastAsyncWorldEdit-Paper-2.15.2-SNAPSHOT-1318.jar"))
    implementation("fr.mrmicky:fastboard:2.1.5")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.hibernate.orm:hibernate-core:6.4.1.Final")
    implementation("org.hibernate.orm:hibernate-hikaricp:6.4.1.Final")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("com.github.HologramLib:HologramLib:1.8.3.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

eclipse {
    jdt {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        javaRuntimeName = "JavaSE-21"
    }
}

checkstyle {
    toolVersion = "10.26.1"
    isIgnoreFailures = true
}

spotbugs {
    toolVersion = "4.9.8"
    ignoreFailures = true
}

tasks.shadowJar {
    archiveFileName.set("TigrouRush.jar")
}

tasks.register<Copy>("copyToPlugins") {
    dependsOn("shadowJar")
    from(tasks.shadowJar)
    into(file("../server/plugins"))
}

tasks.build {
    dependsOn("copyToPlugins")
}
