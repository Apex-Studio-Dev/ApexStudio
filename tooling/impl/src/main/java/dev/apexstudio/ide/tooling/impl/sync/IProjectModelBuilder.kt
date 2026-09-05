package dev.apexstudio.ide.tooling.impl.sync

import dev.apexstudio.ide.project.GradleModels

/**
 * A [model builder][IModelBuilder] used specifically building project models.
 *
 * @author Akash Yadav
 */
interface IProjectModelBuilder<P> : IModelBuilder<P, GradleModels.GradleProject>