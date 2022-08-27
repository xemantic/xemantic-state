/*
 * xemantic-state - a Kotlin library providing hierarchical object state as reactive Flow of events
 * Copyright (C) 2022 Kazimierz Pogoda
 *
 * This file is part of xemantic-state.
 *
 * xemantic-state is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * xemantic-state is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with xemantic-state.
 * If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
  `maven-publish`
  //alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.dokka)
//  id("org.jetbrains.dokka") version "0.10.1"
}

allprojects {

  group = "com.xemantic.state"
  version = "2.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }

  apply {
    //plugin("kotlin")
    plugin("maven-publish")
    //plugin("org.jetbrains.dokka")
  }

}

subprojects {

  /*


   */

  /*
  configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
  }

   */

  /*
  tasks.dokka {
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
  }

  val dokkaJar by tasks.creating(Jar::class) {
    group = JavaBasePlugin.DOCUMENTATION_GROUP
    description = "Assembles Kotlin docs with Dokka"
    archiveClassifier.set("javadoc")
    from(tasks.dokka)
  }


   */
  /*
  publishing {
    publications {
      create<MavenPublication>("default") {
        from(components["java"])
        artifact(dokkaJar)
      }
    }
  }
   */

}
