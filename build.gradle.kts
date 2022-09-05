import org.jetbrains.dokka.gradle.DokkaTask

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
  alias(libs.plugins.dokka)
  id("maven-publish")
}

allprojects {

  group = "com.xemantic.state"
  version = "2.0-SNAPSHOT"

  repositories {
    mavenCentral()
  }

}

tasks.dokkaHtmlMultiModule.configure {
  outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
}

subprojects {

  apply {
    plugin("maven-publish")
  }

  tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
      register("customSourceSet") {
        sourceRoots.from(file("src/commonMain/kotlin"))
        sourceRoots.from(file("src/jvmMain/kotlin"))
      }
    }
  }

}
