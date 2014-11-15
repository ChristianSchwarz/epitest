package org.epitest.runner;

import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.MutationResultListener;

final class MutationResult implements MutationResultListener {

	@Override
	public void runStart() {
	}

	@Override
	public void runEnd() {
	}

	@Override
	public void handleMutationResult(ClassMutationResults results) {

		System.err.println(results);

		// Get the root of the workspace
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		// Get all projects in the workspace
		IProject[] projects = root.getProjects();
		// Loop over all projects
		for (IProject project : projects) {
			try {
				processProjectInfo(project, results);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}

	}

	private void processProjectInfo(IProject project, ClassMutationResults results) throws CoreException, JavaModelException {
		System.out.println("Working in project " + project.getName());
		// check if we have a Java project
		if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
			IJavaProject javaProject = JavaCore.create(project);
			processPackageInfos(results, javaProject);
		}
	}

	private void processPackageInfos(ClassMutationResults results, IJavaProject javaProject) throws CoreException {
		IPackageFragment[] packages = javaProject.getPackageFragments();
		for (IPackageFragment pack : packages) {
			if (pack.getKind() == K_SOURCE && results.getPackageName().equals(pack.getElementName())) {
				System.out.println("Package " + pack.getElementName());
				printICompilationUnitInfo(results, pack);

			}

		}
	}

	private void printICompilationUnitInfo(ClassMutationResults results, IPackageFragment mypackage) throws CoreException {
		for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
			String fileName = results.getFileName();
			String elementName = unit.getElementName();
			if (fileName.equals(elementName)) {
				IResource resource = unit.getResource();
				createMarker(results, resource);
			}
		}
	}

	private void createMarker(ClassMutationResults results, IResource resource) throws CoreException {

		for (org.pitest.mutationtest.MutationResult result : results.getMutations()) {
			IMarker marker = resource.createMarker("org.epitest.mutationmarker");
			String statusDescription = result.getStatusDescription();
			int lineNumber = result.getDetails().getLineNumber();
			marker.setAttribute(IMarker.MESSAGE, statusDescription);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IMarker.CHAR_START, 0);
			marker.setAttribute(IMarker.CHAR_END, 999);

		}

	}
}