package org.epitest.launcher;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Files.createTempDir;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.SEVERE;
import static org.eclipse.core.resources.IResource.DEPTH_INFINITE;
import static org.eclipse.core.runtime.IProgressMonitor.UNKNOWN;
import static org.eclipse.core.runtime.IStatus.ERROR;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT;
import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_LAUNCH_CONFIG;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_MAIN_TYPE;
import static org.epitest.Activator.PLUGIN_ID;
import static org.epitest.launcher.LaunchConfigurationConstants.ATTR_TEST_CONTAINER;
import static org.epitest.report.Markers.MARKER_COVERAGE;
import static org.epitest.report.Markers.MARKER_NO_COVERAGE;
import static org.epitest.report.Markers.MARKER_SURVIVED;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.epitest.Activator;
import org.pitest.functional.Option;
import org.pitest.mutationtest.commandline.OptionsParser;
import org.pitest.mutationtest.commandline.ParseResult;
import org.pitest.mutationtest.commandline.PluginFilter;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.CombinedStatistics;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.util.Log;

/**
 * 
 */
public class LaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

	private String pitestJar = Activator.getDefault().getPitestCorePath();

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.
	 * eclipse.debug.core.ILaunchConfiguration, java.lang.String,
	 * org.eclipse.debug.core.ILaunch,
	 * org.eclipse.core.runtime.IProgressMonitor)
	 */
	public synchronized void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		if (monitor == null)
			monitor = new NullProgressMonitor();

		monitor.beginTask(configuration.getName(), UNKNOWN);

		IJavaProject javaProject = getJavaProject(configuration);
		removeMarker(javaProject.getResource());
		File reportDir = createReportDir();
		try {

			final PluginServices plugins = PluginServices.makeForContextLoader();

			final OptionsParser parser = new OptionsParser(new PluginFilter(plugins));
			String[] argArray = createPiTestArguments(configuration, javaProject, reportDir);
			final ParseResult pr = parser.parse(argArray);

			if (!pr.isOk()) {
				String message = pr.getErrorMessage().value();
				abort(message, null, ERR_UNSPECIFIED_LAUNCH_CONFIG);
			} else {
				final ReportOptions data = pr.getOptions();

				runReport(data, plugins);
			}
		} finally {
			monitor.done();
			reportDir.delete();
		}
	}

	/**
	 * 
	 * @param configuration
	 * @param javaProject
	 * @param reportDir
	 * @return
	 * @throws CoreException
	 */
	private String[] createPiTestArguments(ILaunchConfiguration configuration, IJavaProject javaProject, File reportDir) throws CoreException {
		List<String> classpathList = getClasspathList(configuration);
		List<String> unitsForMutation = getUnitsForMutation(configuration);
		List<String> testUnits = getTestUnits(configuration);
		List<String> sourceFolder = getSourceFolder(javaProject);
		String absolutePath = reportDir.getAbsolutePath();

		unitsForMutation.removeAll(testUnits);
		
		ILaunchConfigurationWorkingCopy workingCopy = configuration.getWorkingCopy();
		workingCopy.setAttribute("classPath", classpathList);
		workingCopy.setAttribute("targetTests", testUnits);
		workingCopy.setAttribute("unitsForMutation", unitsForMutation);

		return new Arguments()//
				.add("--classPath", classpathList)//
				.add("--targetClasses", unitsForMutation) //
				.add("--targetTests", testUnits)//
				.add("--outputFormats", "Epitest") //$NON-NLS-1$
				.add("--sourceDirs", sourceFolder) //
				.add("--reportDir", absolutePath)//
				.add("--verbose", "true")//
				.add("--threads", Runtime.getRuntime().availableProcessors())//
				.add("--timestampedReports", false)//
				.toArray();
	}

	private File createReportDir() {
		File tempDir = createTempDir();
		tempDir.deleteOnExit();
		return tempDir;
	}

	private void removeMarker(IResource resource) throws CoreException {
		removeMarker(resource, MARKER_COVERAGE);
		removeMarker(resource, MARKER_NO_COVERAGE);
		removeMarker(resource, MARKER_SURVIVED);

	}

	private void removeMarker(IResource resource, String type) throws CoreException {
		for (IMarker m : resource.findMarkers(type, true, DEPTH_INFINITE))
			m.delete();
	}

	private CombinedStatistics runReport(final ReportOptions data, PluginServices plugins) throws CoreException {

		List<IStatus> errors = new ArrayList<IStatus>();
		final EntryPoint entryPoint = new EntryPoint();
		final AnalysisResult result;
		try {
			result = entryPoint.execute(null, data, plugins);
			Option<Exception> it = result.getError();
			if (it.hasNone())
				return result.getStatistics().value();

			for (Exception e : it)
				errors.add(new Status(ERROR, PLUGIN_ID, e.getMessage(), e));
		} catch (Throwable e) {
			Logger pitestLog = Log.getLogger();
			pitestLog.log(SEVERE, e.getMessage(), e);

			errors.add(new Status(ERROR, PLUGIN_ID, e.getMessage(), e));
		}

		IStatus[] errorArray = errors.toArray(new IStatus[errors.size()]);
		throw new CoreException(new MultiStatus(PLUGIN_ID, ERROR, errorArray, null, null));

	}

	private static List<String> getSourceFolder(IJavaProject javaProject) throws JavaModelException {
		List<String> result = new ArrayList<String>();
		for (IClasspathEntry e : javaProject.getResolvedClasspath(true))
			if (e.getContentKind() == K_SOURCE)
				result.add(e.getPath().toFile().getAbsolutePath());

		return result;
	}

	private List<String> getUnitsForMutation(ILaunchConfiguration configuration) throws CoreException {
		IJavaProject javaProject = getJavaProject(configuration);
		IPackageFragmentRoot[] allPackageFragmentRoots = javaProject.getAllPackageFragmentRoots();
		List<String> result = new ArrayList<String>();
		for (IPackageFragmentRoot fragmentRoot : allPackageFragmentRoots) {
			if (fragmentRoot.getKind() == K_SOURCE)
				getPackages(result, fragmentRoot);
		}
		return result;
	}

	private List<String> getPackage(IPackageFragment packageFragment) throws JavaModelException {
		if (packageFragment.getKind() != K_SOURCE)
			return emptyList();
		
		List<String> result = new ArrayList<String>();

		IJavaElement[] children = packageFragment.getChildren();
		for (IJavaElement element : children) {
			if (element instanceof ICompilationUnit) {
				ICompilationUnit javaClass = (ICompilationUnit) element;

				String fullClassName = javaClass.findPrimaryType().getFullyQualifiedName();
				result.add(fullClassName);
			} else if (element instanceof IPackageFragment) {
				result.addAll(getPackage((IPackageFragment) element));
			}

		}

		return result;
	}

	private List<String> getTestUnits(ILaunchConfiguration configuration) throws CoreException {
		IJavaProject javaProject = getJavaProject(configuration);
		String containerHandle = configuration.getAttribute(ATTR_TEST_CONTAINER, "");
		String testClassName = configuration.getAttribute(ATTR_MAIN_TYPE_NAME, "");
		if (!testClassName.isEmpty()) {

			return singletonList(testClassName);
		}
		if (!containerHandle.isEmpty()) {

			IJavaElement element = JavaCore.create(containerHandle);
			if (element == null || !element.exists())
				abort("Error input element does not exist! Container-Handle was:" + containerHandle, null, ERR_UNSPECIFIED_MAIN_TYPE);

			int elementType = element.getElementType();
			switch (elementType) {
			case IJavaElement.JAVA_PROJECT:{
				List<String> result = new ArrayList<String>();
				IPackageFragmentRoot[] allPackageFragmentRoots = javaProject.getAllPackageFragmentRoots();
				for (IPackageFragmentRoot packageFragmentRoot : allPackageFragmentRoots)
					getPackages(result, packageFragmentRoot);

				return result;
			}
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:{
				IPackageFragmentRoot packageFragmentRoot = ((IPackageFragmentRoot) element);
				List<String> result = new ArrayList<String>();
				 getPackages(result, packageFragmentRoot);
				return result;}

			case PACKAGE_FRAGMENT:{
				IPackageFragment packageFragment = ((IPackageFragment) element);

				String packageName = packageFragment.getElementName();

				return singletonList(packageName + ".*");
			}
			}
		}

		abort("Error not classes under test found! Container-Handle was:" + containerHandle + " , test class name:" + testClassName, null, ERR_UNSPECIFIED_MAIN_TYPE);
		return emptyList();
	}

	private void getPackages(List<String> result, IPackageFragmentRoot packageFragmentRoot) throws JavaModelException {

		for (IJavaElement e : packageFragmentRoot.getChildren())
			if (e.getElementType() == PACKAGE_FRAGMENT)
				result.addAll(getPackage((IPackageFragment) e));

	}

	private List<String> getClasspathList(ILaunchConfiguration configuration) throws CoreException {
		List<String> classpath = newArrayList(super.getClasspath(configuration));
		classpath.add(pitestJar);

		return classpath;
	}

}
