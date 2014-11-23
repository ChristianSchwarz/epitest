package org.epitest.report;

import static java.util.stream.Collectors.toList;
import static org.eclipse.core.resources.IMarker.CHAR_END;
import static org.eclipse.core.resources.IMarker.CHAR_START;
import static org.eclipse.core.resources.IMarker.LINE_NUMBER;
import static org.eclipse.core.resources.IMarker.MESSAGE;
import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;
import static org.pitest.mutationtest.DetectionStatus.KILLED;
import static org.pitest.mutationtest.DetectionStatus.NO_COVERAGE;
import static org.pitest.mutationtest.DetectionStatus.SURVIVED;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

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
		final SourceFile f = new SourceFile(unit);
		ListMultimap<Integer, MutationResult> m = getLineMutations(results);

		for (Integer lineNumber : m.keys()) {
			List<MutationResult> mutationResults = m.get(lineNumber);
			if (markSurvived(f, mutationResults, lineNumber))
				continue;

			if (markNoCoverage(f, mutationResults, lineNumber))
				continue;

			markKilled(f, mutationResults, lineNumber);

		}

	}

	private ListMultimap<Integer, MutationResult> getLineMutations(ClassMutationResults results) {
		Collection<MutationResult> mutations = results.getMutations();
		ListMultimap<Integer, MutationResult> m = ArrayListMultimap.create(mutations.size(), 2);

		mutations.forEach((MutationResult r) -> {
			MutationDetails details = r.getDetails();
			m.put(details.getLineNumber(), r);
		});
		return m;
	}

	private boolean markKilled(final SourceFile f, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		boolean killed = contains(mutationResults, KILLED);
		if (killed)
			f.createTextMarker("org.epitest.coverage.yes", lineNumber);

		return killed;
	}

	private boolean markNoCoverage(final SourceFile f, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		boolean hasNoCoverage = contains(mutationResults, NO_COVERAGE);
		if (hasNoCoverage)
			f.createTextMarker("org.epitest.coverage.no", lineNumber);

		return hasNoCoverage;
	}

	private boolean markSurvived(final SourceFile f, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		List<MutationResult> lineMutations = filter(mutationResults, SURVIVED);
		if (lineMutations.isEmpty())
			return false;

		for (MutationResult r : lineMutations) {
			String mutationDescription = r.getDetails().getDescription();
			f.createProblemMarker("org.epitest.mutationmarker", lineNumber, mutationDescription);
		}
		f.createTextMarker("org.epitest.mutationmarker", lineNumber);
		return true;
	}

	private static List<MutationResult> filter(List<MutationResult> m, DetectionStatus status) {
		return m.stream().filter(statusIs(status)).collect(toList());
	}

	private static boolean contains(List<MutationResult> m, DetectionStatus status) {
		return m.stream().anyMatch(statusIs(status));
	}

	private static Predicate<MutationResult> statusIs(DetectionStatus status) {
		return (MutationResult r) -> r.getStatus() == status;
	}

	private static final class SourceFile {
		private final IResource resource;
		private final Document document;

		SourceFile(ICompilationUnit unit) throws JavaModelException {
			resource = unit.getResource();

			document = new Document(unit.getSource());
			document.getNumberOfLines();
		}

		public void createProblemMarker(String type, int lineNumber, String message) throws CoreException {
			IMarker m = resource.createMarker(type);
			m.setAttribute(MESSAGE, message);
			m.setAttribute(LINE_NUMBER, lineNumber);
		}

		public void createTextMarker(String type, int lineNumber) throws CoreException {
			IMarker m = resource.createMarker(type);

			int offset = getLineOffset(lineNumber);
			int length = getLineLength(lineNumber);
			m.setAttribute(CHAR_START, offset);
			m.setAttribute(CHAR_END, offset + length);
		}

		private int getLineLength(int lineNumber) {

			try {
				return document.getLineLength(lineNumber - 1);
			} catch (BadLocationException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
		}

		private int getLineOffset(int lineNumber) {
			try {
				return document.getLineOffset(lineNumber - 1);
			} catch (BadLocationException e) {
				throw new IllegalArgumentException(e.getMessage(), e);
			}
		}
	}

}