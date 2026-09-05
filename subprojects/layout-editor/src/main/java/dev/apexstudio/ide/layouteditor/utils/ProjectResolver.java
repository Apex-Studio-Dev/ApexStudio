package dev.apexstudio.ide.layouteditor.utils;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import dev.apexstudio.ide.layouteditor.ProjectFile;
import dev.apexstudio.ide.layouteditor.R;
import dev.apexstudio.ide.layouteditor.managers.ProjectManager;

public class ProjectResolver {
    @Nullable
    public static ProjectFile resolveProject(@Nullable Bundle arguments) {
        if (arguments != null && arguments.containsKey(Constants.EXTRA_KEY_PROJECT)) {
            return arguments.getParcelable(Constants.EXTRA_KEY_PROJECT);
        }
        return ProjectManager.getInstance().getOpenedProject();
    }

    @Nullable
    public static ProjectFile getValidProjectOrShowError(@Nullable Bundle arguments, @Nullable View view) {
        ProjectFile project = resolveProject(arguments);
        if (project == null && view != null) {
            SBUtils.make(view, R.string.msg_error_opening_project).showAsError();
        }
        return project;
    }
}
