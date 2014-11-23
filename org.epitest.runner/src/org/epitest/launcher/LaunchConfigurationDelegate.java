package org.epitest.launcher;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Files.createTempDir;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
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
import static org.epitest.report.Markers.COVERAGE;
import static org.epitest.report.Markers.NO_COVERAGE;
import static org.epitest.report.Markers.SURVIVED;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

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
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.epitest.Activator;
import org.pitest.mutationtest.commandline.OptionsParser;
import org.pitest.mutationtest.commandline.ParseResult;
import org.pitest.mutationtest.commandline.PluginFilter;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.CombinedStatistics;
import org.pitest.mutationtest.tooling.EntryPoint;

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
		return new Arguments()//
				.add("--classPath", getClasspathList(configuration))//
				.add("--targetClasses", getUnitsForMutation(configuration)) //
				.add("--targetTests", getTestUnits(configuration))//
				.add("--outputFormats", "Epitest") //$NON-NLS-1$
				.add("--sourceDirs", getSourceFolder(javaProject)) //
				.add("--reportDir", reportDir.getAbsolutePath())//
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
		removeMarker(resource, COVERAGE);
		removeMarker(resource, NO_COVERAGE);
		removeMarker(resource, SURVIVED);

	}

	private void removeMarker(IResource resource, String type) throws CoreException {
		for (IMarker m : resource.findMarkers(type, true, DEPTH_INFINITE))
			m.delete();
	}

	private CombinedStatistics runReport(final ReportOptions data, PluginServices plugins) throws CoreException {

		final EntryPoint entryPoint = new EntryPoint();
		final AnalysisResult result = entryPoint.execute(null, data, plugins);

		Iterable<Exception> it = result.getError();
		if (result.getError().hasSome()) {
			List<IStatus> errors = stream(it.spliterator(), false)//
					.map((Exception e) -> new Status(ERROR, PLUGIN_ID, e.getMessage(), e))//
					.collect(toList());
			IStatus[] errorArray = errors.toArray(new IStatus[errors.size()]);
			throw new CoreException(new MultiStatus(PLUGIN_ID, ERROR, errorArray, null, null));
		}

		return result.getStatistics().value();

	}

	//

	private static List<String> getSourceFolder(IJavaProject javaProject) throws JavaModelException {

		IClasspathEntry[] classpathEntries = javaProject.getResolvedClasspath(true);
		Predicate<IClasspathEntry> sourcesEntries = (IClasspathEntry entry) -> entry.getContentKind() == K_SOURCE;
		Function<IClasspathEntry, String> absolutePath = (IClasspathEntry entry) -> entry.getPath().toFile().getAbsolutePath();
		return stream(classpathEntries)//
				.filter(sourcesEntries)//
				.map(absolutePath)//
				.collect(toList());

	}

	private List<String> getUnitsForMutation(ILaunchConfiguration configuration) throws CoreException {
		IJavaProject javaProject = getJavaProject(configuration);
		String containerHandle = configuration.getAttribute(ATTR_TEST_CONTAINER, "");
		String testClassName = configuration.getAttribute(ATTR_MAIN_TYPE_NAME, "");
		if (!testClassName.isEmpty()) {
			IType t = javaProject.findType(testClassName);
			IPackageFragment packageFragment = t.getPackageFragment();

			return singletonList(packageFragment.getElementName() + ".*");
		}
		if (!containerHandle.isEmpty()) {

			IJavaElement element = JavaCore.create(containerHandle);
			if (element == null || !element.exists())
				abort("Error input element does not exist! Container-Handle was:" + containerHandle, null, ERR_UNSPECIFIED_MAIN_TYPE);

			int elementType = element.getElementType();
			switch (elementType) {
			// case IJavaElement.JAVA_PROJECT:
			// javaProject.getAllPackageFragmentRoots()

			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				IPackageFragmentRoot packageFragmentRoot = ((IPackageFragmentRoot) element);

				List<String> packages = stream(packageFragmentRoot.getChildren())//
						.filter((IJavaElement e) -> e.getElementType() == PACKAGE_FRAGMENT)//
						.map((IJavaElement e) -> e.getElementName() + ".*")//
						.collect(toList());
				return packages;

			case PACKAGE_FRAGMENT:
				IPackageFragment packageFragment = ((IPackageFragment) element);

				String packageName = packageFragment.getElementName();

				return singletonList(packageName + ".*");
			}
		}

		abort("Error not classes under test found! Container-Handle was:" + containerHandle + " , test class name:" + testClassName, null, ERR_UNSPECIFIED_MAIN_TYPE);
		return emptyList();
	}

	private List<String> getTestUnits(ILaunchConfiguration configuration) throws CoreException {
		// IJavaProject javaProject = getJavaProject(configuration);
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
			// case IJavaElement.JAVA_PROJECT:
			// javaProject.getAllPackageFragmentRoots()

			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				IPackageFragmentRoot packageFragmentRoot = ((IPackageFragmentRoot) element);

				List<String> packages = stream(packageFragmentRoot.getChildren())//
						.filter((IJavaElement e) -> e.getElementType() == PACKAGE_FRAGMENT)//
						.map((IJavaElement e) -> e.getElementName() + ".*")//
						.collect(toList());
				return packages;

			case PACKAGE_FRAGMENT:
				IPackageFragment packageFragment = ((IPackageFragment) element);

				String packageName = packageFragment.getElementName();

				return singletonList(packageName + ".*");
			}
		}

		abort("Error not classes under test found! Container-Handle was:" + containerHandle + " , test class name:" + testClassName, null, ERR_UNSPECIFIED_MAIN_TYPE);
		return emptyList();
	}

	private List<String> getClasspathList(ILaunchConfiguration configuration) throws CoreException {
		List<String> classpath = newArrayList(super.getClasspath(configuration));
		classpath.add(pitestJar);

		return classpath;
	}

	private static class Arguments {

		private final List<String> args = new ArrayList<>();

		Arguments add(String attribute, String value) {
			args.add(checkNotNull(attribute));
			args.add(checkNotNull(value));
			return this;
		}

		public Arguments add(String attribute, boolean b) {
			return add(attribute, Boolean.toString(b));
		}

		public Arguments add(String attribute, Number value) {
			return add(attribute, value.toString());
		}

		Arguments add(String attribute, Collection<String> values) {
			return add(attribute, on(',').join(values));
		}

		String[] toArray() {
			return args.toArray(new String[args.size()]);
		}
	}

}
