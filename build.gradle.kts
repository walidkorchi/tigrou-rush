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

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }

    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0")
    compileOnly(files("../server/plugins/GMusic-2.1.2.jar"))
    implementation("fr.mrmicky:fastboard:2.1.5")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.hibernate.orm:hibernate-core:6.4.1.Final")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    compileOnly("com.github.HologramLib:HologramLib:1.8.3.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.test {
    useJUnitPlatform()
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
