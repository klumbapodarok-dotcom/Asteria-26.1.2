import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile
import java.net.URLClassLoader

val bundledFiguraJar = layout.projectDirectory.file("libs/figura-0.1.6-port.1+26.1.2-fabric-mc.jar")

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.3.21"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("asteria") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

sourceSets.named("client") {
	kotlin.srcDir("src/client/kotlin")
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
	
	add("clientImplementation", "net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	add("clientImplementation", "net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	add("clientImplementation", "net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
	add("clientRuntimeOnly", files(bundledFiguraJar))
	add("clientCompileOnly", files(bundledFiguraJar))
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

val geometryTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs rounded rectangle geometry regression checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.RoundedRectangleGeometryTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val sprintCritPauseTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs sprint crit suppression regression checks."
	dependsOn(tasks.compileTestKotlin)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.module.modules.movement.SprintCritPauseTestKt")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val criticalAttackQueueTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs critical attack delay regression checks."
	dependsOn(tasks.compileTestKotlin)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.module.modules.combat.CriticalAttackQueueTestKt")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val combatInfrastructureContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs combat infrastructure source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.CombatInfrastructureContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val autoTrapAuraContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs AutoTrap and Aura integration source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.AutoTrapAuraContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val fakePlayerContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs Fake Player and AutoTrap movement-filter source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.FakePlayerContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val killauraSnapRotationContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs Aura no-snap rotation source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.KillauraSnapRotationContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val killauraSprintResetContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs Aura sprint reset timing source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.KillauraSprintResetContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val liquidFogContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs Liquid Fog source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.LiquidFogContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val targetEspContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs Target ESP source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.TargetEspContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

val viewmodelAuraContractTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs View Model and Aura viewmodel source contract checks."
	dependsOn(tasks.compileTestJava)
	classpath = sourceSets["test"].runtimeClasspath
	mainClass.set("asteria.top.client.util.ViewmodelAuraContractTest")
	javaLauncher.set(javaToolchains.launcherFor {
		languageVersion.set(JavaLanguageVersion.of(25))
	})
}

tasks.test {
	failOnNoDiscoveredTests = false
}

tasks.check {
	dependsOn(geometryTest)
	dependsOn(sprintCritPauseTest)
	dependsOn(criticalAttackQueueTest)
	dependsOn(combatInfrastructureContractTest)
	dependsOn(autoTrapAuraContractTest)
	dependsOn(fakePlayerContractTest)
	dependsOn(killauraSnapRotationContractTest)
	dependsOn(killauraSprintResetContractTest)
	dependsOn(liquidFogContractTest)
	dependsOn(targetEspContractTest)
	dependsOn(viewmodelAuraContractTest)
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	withSourcesJar()

	toolchain {
		languageVersion.set(JavaLanguageVersion.of(25))
	}
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}

	from(bundledFiguraJar) {
		into("META-INF/jars")
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	repositories {
	}
}

tasks.register("inspectMinecraftClasses") {
	doLast {
		val classpathFiles = configurations.getByName("clientCompileClasspath").files
		val urls = classpathFiles.map { it.toURI().toURL() }.toTypedArray()
		val classLoader = URLClassLoader(urls, ClassLoader.getSystemClassLoader())
		
		try {
			val renderTypeClass = classLoader.loadClass("net.minecraft.client.renderer.rendertype.RenderType")
			println("--- RenderType Constructors ---")
			for (c in renderTypeClass.declaredConstructors) {
				println("Constructor: ${c.parameterTypes.map { it.name }.joinToString()}")
			}
			println("--- RenderType Fields ---")
			for (f in renderTypeClass.declaredFields) {
				println("Field: ${f.name} - ${f.type.name}")
			}
		} catch (e: Exception) {
			println("Failed to load RenderType: ${e.message}")
		}

		try {
			val lersClass = classLoader.loadClass("net.minecraft.client.renderer.entity.state.LivingEntityRenderState")
			println("--- LivingEntityRenderState Fields ---")
			for (f in lersClass.fields) {
				println("Field: ${f.name} - ${f.type.name}")
			}
			println("--- LivingEntityRenderState Methods ---")
			for (m in lersClass.declaredMethods) {
				println("Method: ${m.name}")
			}
		} catch (e: Exception) {
			println("Failed to load LivingEntityRenderState: ${e.message}")
		}

		try {
			val ersClass = classLoader.loadClass("net.minecraft.client.renderer.entity.state.EntityRenderState")
			println("--- EntityRenderState Fields ---")
			for (f in ersClass.fields) {
				println("Field: ${f.name} - ${f.type.name}")
			}
		} catch (e: Exception) {
			println("Failed to load EntityRenderState: ${e.message}")
		}

		try {
			val compareOpClass = classLoader.loadClass("com.mojang.blaze3d.platform.CompareOp")
			println("--- CompareOp values ---")
			for (f in compareOpClass.declaredFields) {
				if (f.isEnumConstant) {
					println("Enum constant: ${f.name}")
				}
			}
		} catch (e: Exception) {
			println("Failed to load CompareOp: ${e.message}")
		}
	}
}
