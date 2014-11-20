package org.epitest.runner;

import static org.eclipse.core.resources.IMarker.CHAR_END;
import static org.eclipse.core.resources.IMarker.CHAR_START;
import static org.eclipse.core.resources.IMarker.LINE_NUMBER;
import static org.eclipse.core.resources.IMarker.MESSAGE;
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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.engine.MutationDetails;

final class ResultListener implements MutationResultListener {

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

				createMarker(results, unit);
			}
		}
	}

	private void createMarker(ClassMutationResults results, ICompilationUnit unit) throws CoreException {
		IResource resource = unit.getResource();

		Document document = new Document(unit.getSource());
		for (MutationResult result : results.getMutations()) {


			
			MutationDetails details = result.getDetails();
			
			String mutationDdescription = details.getDescription();
			int lineNumber = details.getLineNumber();
			
			int offset = getLineOffset(document, lineNumber);
			int length = getLineLength(document, lineNumber);
			
		
			
			DetectionStatus status = result.getStatus();
			switch (status) {

			case SURVIVED:
				createMutationDescription(resource, mutationDdescription, lineNumber);
				highlightMutation(resource, offset, length);
				
				break;
			case NO_COVERAGE:
				highlightMissingCoverage(resource, offset, length);
				break;
			case KILLED:
				highlightCoverage(resource, offset, length);
				break;
			default:
				break;
			}

		}

	}

	private void highlightMutation(IResource resource, int offset, int lineLength) throws CoreException {
		IMarker coverageMarker = resource.createMarker("org.epitest.mutationmarker");
		coverageMarker.setAttribute(CHAR_START, offset);
		coverageMarker.setAttribute(CHAR_END, offset + lineLength);
	}
	
	private void highlightCoverage(IResource resource, int offset, int lineLength) throws CoreException {
		IMarker coverageMarker = resource.createMarker("org.epitest.coverage.yes");
		coverageMarker.setAttribute(CHAR_START, offset);
		coverageMarker.setAttribute(CHAR_END, offset + lineLength);
	}

	private void highlightMissingCoverage(IResource resource, int offset, int lineLength) throws CoreException {
		IMarker coverageMarker = resource.createMarker("org.epitest.coverage.no");
		coverageMarker.setAttribute(CHAR_START, offset);
		coverageMarker.setAttribute(CHAR_END, offset + lineLength);
	}

	private void createMutationDescription(IResource resource, String statusDescription, int lineNumber) throws CoreException {
		IMarker mutationMarker = resource.createMarker("org.epitest.mutationmarker");
		mutationMarker.setAttribute(MESSAGE, statusDescription);
		mutationMarker.setAttribute(LINE_NUMBER, lineNumber);
	}

	private int getLineLength(Document document, int lineNumber) {
		try {
			return document.getLineLength(lineNumber - 1);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	private int getLineOffset(Document document, int lineNumber) {
		try {
			return document.getLineOffset(lineNumber - 1);
		} catch (BadLocationException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}
}