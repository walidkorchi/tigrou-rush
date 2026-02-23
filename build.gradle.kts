plugins {
    id("checkstyle")
    id("com.github.spotbugs") version "6.4.8"
    id("com.gradleup.shadow") version "9.3.1"
    id("java")
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

    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly(files("../rush-tl-events/plugins/worldedit-bukkit-7.4.0.jar"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.shadowJar {
    archiveFileName.set("TigrouRush.jar")
}

tasks.register<Copy>("copyToPlugins") {
    dependsOn("shadowJar")
    from(tasks.shadowJar)
    into(file("../rush-tl-events/plugins"))
}

tasks.build {
    dependsOn("copyToPlugins")
}
