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

package dev.apexstudio.ide.handlers

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.apexstudio.ide.eventbus.events.Event
import dev.apexstudio.ide.eventbus.events.EventReceiver
import dev.apexstudio.ide.eventbus.events.editor.OnCreateEvent
import dev.apexstudio.ide.eventbus.events.editor.OnDestroyEvent
import dev.apexstudio.ide.eventbus.events.editor.OnPauseEvent
import dev.apexstudio.ide.eventbus.events.editor.OnResumeEvent
import dev.apexstudio.ide.eventbus.events.editor.OnStartEvent
import dev.apexstudio.ide.eventbus.events.editor.OnStopEvent
import dev.apexstudio.ide.projects.ProjectManagerImpl
import dev.apexstudio.ide.projects.util.BootClasspathProvider
import dev.apexstudio.ide.utils.EditorActivityActions
import dev.apexstudio.ide.utils.EditorSidebarActions
import dev.apexstudio.ide.utils.Environment
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.CompletableFuture

/**
 * Observes lifecycle events if [dev.apexstudio.ide.EditorActivityKt].
 *
 * @author Akash Yadav
 */
class EditorActivityLifecyclerObserver : DefaultLifecycleObserver {

  private val fileActionsHandler = FileTreeActionHandler()

  override fun onCreate(owner: LifecycleOwner) {
    EditorActivityActions.register(owner as Context)
    EditorSidebarActions.registerActions(owner as Context)
    dispatchEvent(OnCreateEvent())
  }

  override fun onStart(owner: LifecycleOwner) {
    CompletableFuture.runAsync(this::initBootclasspathProvider)
    register(fileActionsHandler, ProjectManagerImpl.getInstance())

    dispatchEvent(OnStartEvent())
  }

  override fun onResume(owner: LifecycleOwner) {
    EditorActivityActions.register(owner as Context)
    dispatchEvent(OnResumeEvent())
  }

  override fun onPause(owner: LifecycleOwner) {
    EditorActivityActions.clear()
    dispatchEvent(OnPauseEvent())
  }

  override fun onStop(owner: LifecycleOwner) {
    unregister(fileActionsHandler, ProjectManagerImpl.getInstance())
    dispatchEvent(OnStopEvent())
  }

  override fun onDestroy(owner: LifecycleOwner) {
    dispatchEvent(OnDestroyEvent())
  }

  private fun register(vararg receivers: EventReceiver) {
    receivers.forEach { it.register() }
  }

  private fun unregister(vararg receivers: EventReceiver) {
    receivers.forEach { it.unregister() }
  }

  private fun dispatchEvent(event: Event) {
    EventBus.getDefault().post(event)
  }

  private fun initBootclasspathProvider() {
    BootClasspathProvider.update(listOf(Environment.ANDROID_JAR.absolutePath))
  }
}
