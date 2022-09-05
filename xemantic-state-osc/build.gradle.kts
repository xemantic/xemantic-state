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
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.dokka)
}

kotlin {

  jvm {
    compilations.all {
      kotlinOptions.jvmTarget = libs.versions.jvmTarget.get()
    }
    testRuns["test"].executionTask.configure {
      useJUnitPlatform()
    }
  }

  sourceSets {

    val commonMain by getting {
      dependencies {
        api(project(":xemantic-state-core"))
      }
    }

    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlin.coroutines.test)
        implementation(libs.kotest)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.java.osc)
        implementation(libs.kotlin.reflect)
        implementation(libs.kotlin.logging)
      }
      configurations {
        all {
          exclude("log4j", "log4j")
          exclude("org.slf4j", "slf4j-log4j12")
        }
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(libs.slf4j.api)
        implementation(libs.slf4j.simple)
      }
    }

  }

}