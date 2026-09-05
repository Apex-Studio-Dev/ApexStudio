package dev.apexstudio.ide.projects.models

import dev.apexstudio.ide.project.JavaModels
import java.io.File

val JavaModels.JavaSourceDirectoryOrBuilder.directory: File
	get() = File(directoryPath)

val JavaModels.JavaDependencyOrBuilder.jarFile: File
	get() = File(jarFilePath)
