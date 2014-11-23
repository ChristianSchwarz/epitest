package org.epitest.launcher.ui;

import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;


public final class MigrationDelegate {

	private MigrationDelegate() {
	}

	public static void mapResources(ILaunchConfigurationWorkingCopy config)
			throws CoreException {
		IResource resource = getResource(config);
		if (resource == null) {
			config.setMappedResources(null);
		} else {
			config.setMappedResources(new IResource[] { resource });
		}
	}

	/**
	 * Returns a resource mapping for the given launch configuration, or
	 * <code>null</code> if none.
	 * 
	 * @param config
	 *            working copy
	 * @return resource or <code>null</code>
	 * @throws CoreException
	 *             if an exception occurs mapping resource
	 */
	private static IResource getResource(ILaunchConfiguration config)
			throws CoreException {
		String projName = config.getAttribute(ATTR_PROJECT_NAME, (String) null);
		String typeName = config.getAttribute(ATTR_MAIN_TYPE_NAME,
				(String) null);

		IJavaElement element = null;
		if (projName != null && Path.ROOT.isValidSegment(projName)) {
			IJavaProject javaProject = getJavaModel().getJavaProject(projName);
			if (javaProject.exists()) {
				if (typeName != null && typeName.length() > 0) {
					element = javaProject.findType(typeName);
				} 
				if (element == null) {
					element = javaProject;
				}
			} else {
				IProject project = javaProject.getProject();
				if (project.exists() && !project.isOpen()) {
					return project;
				}
			}
		}
		IResource resource = null;
		if (element != null) {
			resource = element.getResource();
		}
		return resource;
	}

	/*
	 * Convenience method to get access to the java model.
	 */
	private static IJavaModel getJavaModel() {
		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
	}
}
