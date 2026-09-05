package dev.apexstudio.ide.projects.models

import dev.apexstudio.ide.project.GradleModels
import java.io.File

val GradleModels.GradleProjectOrBuilder.projectDir: File
	get() = File(projectDirPath)

val GradleModels.GradleProjectOrBuilder.buildDir: File
	get() = File(buildDirPath)
