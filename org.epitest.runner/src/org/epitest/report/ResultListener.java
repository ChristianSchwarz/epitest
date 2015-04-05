package org.epitest.report;

import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;
import static org.epitest.report.Markers.createMarkersAtLine;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
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
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.engine.MutationDetails;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

final class ResultListener implements MutationResultListener {

	@Override
	public void runStart() {
	}

	@Override
	public void runEnd() {
	}

	@Override
	public void handleMutationResult(ClassMutationResults results) {

		// Get the root of the workspace
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();

		// Loop over all projects in the workspace
		for (IProject project : root.getProjects())
			try {
				// check if we have a Java project
				if (project.isOpen()  && project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
					IJavaProject javaProject = JavaCore.create(project);
					traversePackages(results, javaProject);
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}

	}

	private void traversePackages(ClassMutationResults results, IJavaProject javaProject) throws CoreException {
		IPackageFragment[] packages = javaProject.getPackageFragments();
		for (IPackageFragment _package : packages)
			if (hasSource(_package) && equalPackages(results, _package))
				traverseSourceFiles(results, _package);

	}

	private void traverseSourceFiles(ClassMutationResults results, IPackageFragment packageFragment) throws CoreException {
		for (ICompilationUnit sourceFile : packageFragment.getCompilationUnits())
			if (isFileNameEqual(results, sourceFile))
				createMarkers(results, sourceFile);

	}

	private void createMarkers(ClassMutationResults results, ICompilationUnit sourceFile) throws CoreException {
		final SourceFile f = new SourceFile(sourceFile);
		ListMultimap<Integer, MutationResult> m = getLineMutations(results);

		for (Integer lineNumber : m.keys()) {
			List<MutationResult> mutationResults = m.get(lineNumber);
			createMarkersAtLine(f, mutationResults, lineNumber);

		}

	}


	private ListMultimap<Integer, MutationResult> getLineMutations(ClassMutationResults results) {
		Collection<MutationResult> mutations = results.getMutations();
		ListMultimap<Integer, MutationResult> m = ArrayListMultimap.create(mutations.size(), 2);

		for (MutationResult r : mutations) {
			MutationDetails details = r.getDetails();
			int lineNumber = details.getLineNumber();
			m.put(lineNumber, r);
		}
		
		return m;
	}



	

	private static boolean equalPackages(ClassMutationResults results, IPackageFragment _package) {
		return results.getPackageName().equals(_package.getElementName());
	}

	private static boolean hasSource(IPackageFragment packageFragment) throws JavaModelException {
		return packageFragment.getKind() == K_SOURCE;
	}

	private boolean isFileNameEqual(ClassMutationResults results, ICompilationUnit unit) {
		return results.getFileName().equals(unit.getElementName());
	}

}