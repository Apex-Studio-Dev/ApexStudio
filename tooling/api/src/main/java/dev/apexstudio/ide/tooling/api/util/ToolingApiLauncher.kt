/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.apexstudio.ide.tooling.api.util

import com.google.gson.GsonBuilder
import dev.apexstudio.ide.builder.model.DefaultJavaCompileOptions
import dev.apexstudio.ide.builder.model.IJavaCompilerSettings
import dev.apexstudio.ide.tooling.api.IToolingApiClient
import dev.apexstudio.ide.tooling.api.IToolingApiServer
import dev.apexstudio.ide.tooling.api.messages.GradleBuildParams
import dev.apexstudio.ide.tooling.api.messages.InitializeProjectParams
import dev.apexstudio.ide.tooling.api.messages.TaskExecutionMessage
import dev.apexstudio.ide.tooling.api.messages.result.InitializeResult
import dev.apexstudio.ide.tooling.api.models.AndroidProjectMetadata
import dev.apexstudio.ide.tooling.api.models.AndroidVariantMetadata
import dev.apexstudio.ide.tooling.api.models.BasicAndroidVariantMetadata
import dev.apexstudio.ide.tooling.api.models.BasicProjectMetadata
import dev.apexstudio.ide.tooling.api.models.GradleTask
import dev.apexstudio.ide.tooling.api.models.JavaModuleCompilerSettings
import dev.apexstudio.ide.tooling.api.models.JavaModuleDependency
import dev.apexstudio.ide.tooling.api.models.JavaModuleExternalDependency
import dev.apexstudio.ide.tooling.api.models.JavaModuleProjectDependency
import dev.apexstudio.ide.tooling.api.models.JavaProjectMetadata
import dev.apexstudio.ide.tooling.api.models.Launchable
import dev.apexstudio.ide.tooling.api.models.ProjectMetadata
import dev.apexstudio.ide.tooling.events.OperationDescriptor
import dev.apexstudio.ide.tooling.events.OperationResult
import dev.apexstudio.ide.tooling.events.ProgressEvent
import dev.apexstudio.ide.tooling.events.StatusEvent
import dev.apexstudio.ide.tooling.events.configuration.ProjectConfigurationFinishEvent
import dev.apexstudio.ide.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import dev.apexstudio.ide.tooling.events.configuration.ProjectConfigurationOperationResult
import dev.apexstudio.ide.tooling.events.configuration.ProjectConfigurationProgressEvent
import dev.apexstudio.ide.tooling.events.configuration.ProjectConfigurationStartEvent
import dev.apexstudio.ide.tooling.events.download.FileDownloadFinishEvent
import dev.apexstudio.ide.tooling.events.download.FileDownloadOperationDescriptor
import dev.apexstudio.ide.tooling.events.download.FileDownloadProgressEvent
import dev.apexstudio.ide.tooling.events.download.FileDownloadResult
import dev.apexstudio.ide.tooling.events.download.FileDownloadStartEvent
import dev.apexstudio.ide.tooling.events.internal.DefaultFinishEvent
import dev.apexstudio.ide.tooling.events.internal.DefaultOperationDescriptor
import dev.apexstudio.ide.tooling.events.internal.DefaultOperationResult
import dev.apexstudio.ide.tooling.events.internal.DefaultProgressEvent
import dev.apexstudio.ide.tooling.events.internal.DefaultStartEvent
import dev.apexstudio.ide.tooling.events.task.TaskExecutionResult
import dev.apexstudio.ide.tooling.events.task.TaskFailureResult
import dev.apexstudio.ide.tooling.events.task.TaskFinishEvent
import dev.apexstudio.ide.tooling.events.task.TaskOperationDescriptor
import dev.apexstudio.ide.tooling.events.task.TaskOperationResult
import dev.apexstudio.ide.tooling.events.task.TaskProgressEvent
import dev.apexstudio.ide.tooling.events.task.TaskSkippedResult
import dev.apexstudio.ide.tooling.events.task.TaskStartEvent
import dev.apexstudio.ide.tooling.events.task.TaskSuccessResult
import dev.apexstudio.ide.tooling.events.test.TestFinishEvent
import dev.apexstudio.ide.tooling.events.test.TestOperationDescriptor
import dev.apexstudio.ide.tooling.events.test.TestOperationResult
import dev.apexstudio.ide.tooling.events.test.TestProgressEvent
import dev.apexstudio.ide.tooling.events.test.TestStartEvent
import dev.apexstudio.ide.tooling.events.transform.TransformFinishEvent
import dev.apexstudio.ide.tooling.events.transform.TransformOperationDescriptor
import dev.apexstudio.ide.tooling.events.transform.TransformProgressEvent
import dev.apexstudio.ide.tooling.events.transform.TransformStartEvent
import dev.apexstudio.ide.tooling.events.work.WorkItemFinishEvent
import dev.apexstudio.ide.tooling.events.work.WorkItemOperationDescriptor
import dev.apexstudio.ide.tooling.events.work.WorkItemOperationResult
import dev.apexstudio.ide.tooling.events.work.WorkItemProgressEvent
import dev.apexstudio.ide.tooling.events.work.WorkItemStartEvent
import org.eclipse.lsp4j.jsonrpc.Launcher
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Utility class for launching [IToolingApiClient] and [IToolingApiServer].
 *
 * @author Akash Yadav
 */
object ToolingApiLauncher {
	fun <T> createIOLauncher(
		local: Any?,
		remote: Class<T>?,
		`in`: InputStream?,
		out: OutputStream?,
	): Launcher<T> =
		Launcher
			.Builder<T>()
			.setInput(`in`)
			.setOutput(out)
			.setLocalService(local)
			.setRemoteInterface(remote)
			.configureGson { configureGson(it) }
			.create()

	@JvmStatic
	fun configureGson(builder: GsonBuilder) {
		builder.registerTypeAdapter(File::class.java, FileTypeAdapter())

		builder.runtimeTypeAdapter(
			InitializeResult::class.java,
			InitializeResult.Success::class.java,
			InitializeResult.Failure::class.java,
		)
		builder.runtimeTypeAdapter(
			GradleBuildParams::class.java,
			InitializeProjectParams::class.java,
			TaskExecutionMessage::class.java,
		)

		// some methods return BasicProjectMetadata while some return ProjectMetadata
		// so we need to register type adapter for both of them
		builder.runtimeTypeAdapter(
			BasicProjectMetadata::class.java,
			ProjectMetadata::class.java,
			AndroidProjectMetadata::class.java,
			JavaProjectMetadata::class.java,
		)
		builder.runtimeTypeAdapter(
			ProjectMetadata::class.java,
			AndroidProjectMetadata::class.java,
			JavaProjectMetadata::class.java,
		)
		builder.runtimeTypeAdapter(
			BasicAndroidVariantMetadata::class.java,
			AndroidVariantMetadata::class.java,
		)
		builder.runtimeTypeAdapter(
			JavaModuleDependency::class.java,
			JavaModuleExternalDependency::class.java,
			JavaModuleProjectDependency::class.java,
		)
		builder.runtimeTypeAdapter(
			IJavaCompilerSettings::class.java,
			DefaultJavaCompileOptions::class.java,
			JavaModuleCompilerSettings::class.java,
		)
		builder.runtimeTypeAdapter(
			Launchable::class.java,
			GradleTask::class.java,
		)
		builder.runtimeTypeAdapter(
			ProgressEvent::class.java,
			ProjectConfigurationProgressEvent::class.java,
			ProjectConfigurationStartEvent::class.java,
			ProjectConfigurationFinishEvent::class.java,
			FileDownloadProgressEvent::class.java,
			FileDownloadStartEvent::class.java,
			FileDownloadFinishEvent::class.java,
			TaskProgressEvent::class.java,
			TaskStartEvent::class.java,
			TaskFinishEvent::class.java,
			TestProgressEvent::class.java,
			TestStartEvent::class.java,
			TestFinishEvent::class.java,
			TransformProgressEvent::class.java,
			TransformStartEvent::class.java,
			TransformFinishEvent::class.java,
			WorkItemProgressEvent::class.java,
			WorkItemStartEvent::class.java,
			WorkItemFinishEvent::class.java,
			DefaultProgressEvent::class.java,
			DefaultStartEvent::class.java,
			DefaultFinishEvent::class.java,
			StatusEvent::class.java,
		)
		builder.runtimeTypeAdapter(
			OperationDescriptor::class.java,
			ProjectConfigurationOperationDescriptor::class.java,
			FileDownloadOperationDescriptor::class.java,
			TaskOperationDescriptor::class.java,
			TestOperationDescriptor::class.java,
			TransformOperationDescriptor::class.java,
			WorkItemOperationDescriptor::class.java,
			DefaultOperationDescriptor::class.java,
		)
		builder.runtimeTypeAdapter(
			OperationResult::class.java,
			ProjectConfigurationOperationResult::class.java,
			FileDownloadResult::class.java,
			TaskOperationResult::class.java,
			TestOperationResult::class.java,
			WorkItemOperationResult::class.java,
			DefaultOperationResult::class.java,
		)
		builder.runtimeTypeAdapter(
			TaskOperationResult::class.java,
			TaskFailureResult::class.java,
			TaskSkippedResult::class.java,
			TaskExecutionResult::class.java,
			TaskSuccessResult::class.java,
		)
	}

	private fun <T> GsonBuilder.runtimeTypeAdapter(
		baseClass: Class<T>,
		vararg subtypes: Class<out T>,
	) {
		registerTypeAdapterFactory(
			RuntimeTypeAdapterFactory
				.of(baseClass, "gsonType", true)
				.registerSubtype(baseClass, baseClass.name)
				.also { factory ->
					subtypes.forEach { subtype ->
						factory.registerSubtype(subtype, subtype.name)
					}
				},
		)
	}

	fun newClientLauncher(
		client: IToolingApiClient,
		`in`: InputStream?,
		out: OutputStream?,
		executorService: ExecutorService = Executors.newCachedThreadPool(),
	): Launcher<Any> = newIoLauncher(arrayOf(client), arrayOf(IToolingApiServer::class.java), `in`, out, executorService)

	fun newIoLauncher(
		locals: Array<Any>,
		remotes: Array<Class<*>?>,
		`in`: InputStream?,
		out: OutputStream?,
		executorService: ExecutorService = Executors.newCachedThreadPool(),
	): Launcher<Any> =
		Launcher
			.Builder<Any>()
			.setInput(`in`)
			.setOutput(out)
			.setExecutorService(executorService)
			.setLocalServices(listOf(*locals))
			.setRemoteInterfaces(listOf(*remotes))
			.configureGson { configureGson(it) }
			.setClassLoader(locals[0].javaClass.classLoader)
			.create()

	@JvmStatic
	fun newServerLauncher(
		server: IToolingApiServer,
		`in`: InputStream?,
		out: OutputStream?,
		executorService: ExecutorService = Executors.newCachedThreadPool(),
	): Launcher<Any> =
		newIoLauncher(
			arrayOf(server),
			arrayOf(
				IToolingApiClient::class.java,
			),
			`in`,
			out,
			executorService,
		)
}