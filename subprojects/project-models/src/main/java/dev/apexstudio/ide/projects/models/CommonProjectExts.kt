package dev.apexstudio.ide.projects.models

import dev.apexstudio.ide.project.JavaCompilerSettings

val DEFAULT_COMPILER_SETTINGS =
	JavaCompilerSettings(
		sourceCompatibility = "RELEASE_11",
		targetCompatibility = "RELEASE_11",
	)
