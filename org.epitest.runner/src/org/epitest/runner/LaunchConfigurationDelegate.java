package org.epitest.runner;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Files.createTempDir;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.core.runtime.IStatus.ERROR;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT;
import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_MAIN_TYPE;
import static org.epitest.runner.LaunchConfigurationConstants.ATTR_TEST_CONTAINER;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.pitest.coverage.CoverageSummary;
import org.pitest.functional.FCollection;
import org.pitest.mutationtest.commandline.OptionsParser;
import org.pitest.mutationtest.commandline.ParseResult;
import org.pitest.mutationtest.commandline.PluginFilter;
import org.pitest.mutationtest.config.PluginServices;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.statistics.MutationStatistics;
import org.pitest.mutationtest.tooling.AnalysisResult;
import org.pitest.mutationtest.tooling.CombinedStatistics;
import org.pitest.mutationtest.tooling.EntryPoint;
import org.pitest.plugin.ToolClasspathPlugin;
import org.pitest.util.Glob;
import org.pitest.util.IsolationUtils;
import org.pitest.util.Unchecked;

import com.google.common.collect.Lists;

/**
 * Launch configuration delegate for a JUnit test as a Java application.
 * 
 * <p>
 * Clients can instantiate and extend this class.
 * </p>
 * 
 * @since 3.3
 */
public class LaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {


	private String pitestCommandLineJar = Activator.getDefault().getPitestCmdLinePath();
	private String pitestJar = Activator.getDefault().getPitestCorePath();

	

	public LaunchConfigurationDelegate() {
		
	}

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
		IJavaProject javaProject = getJavaProject(configuration);
		File tempDir = createTempDir();
		tempDir.deleteOnExit();

		String reportDir = tempDir.getAbsolutePath();
		
		Arguments args = new Arguments()//
				.add("--classPath", getClasspathList(configuration))//
				.add("--targetClasses", getUnitsForMutation(configuration)) //
				.add("--targetTests", getTestUnits(configuration))//
				.add("--outputFormats", "XML,CSV,HTML,Epitest") //$NON-NLS-1$
				.add("--sourceDirs", getSourceFolder(javaProject)) //
				.add("--reportDir", reportDir)//
				.add("--verbose", "true");

		final PluginServices plugins = PluginServices.makeForContextLoader();
		
		final OptionsParser parser = new OptionsParser(new PluginFilter(plugins));
		String[] argArray = args.toArray();
		final ParseResult pr = parser.parse(argArray);

		if (!pr.isOk()) {
			tempDir.delete();
			String message = pr.getErrorMessage().value();
			System.out.println(">>>> " + message);
			throw new CoreException(new Status(ERROR, "epitest", message));
		} else {
			final ReportOptions data = pr.getOptions();

			final CombinedStatistics stats = runReport(data, plugins);
			MutationStatistics mutationStatistics = stats.getMutationStatistics();
			mutationStatistics.report(System.err);
			
			
		}

	}

	private static CombinedStatistics runReport(final ReportOptions data, PluginServices plugins) {

		final EntryPoint e = new EntryPoint();
		final AnalysisResult result = e.execute(null, data, plugins);
		if (result.getError().hasSome()) {
			throw Unchecked.translateCheckedException(result.getError().value());
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

	public List<String> getClasspathList(ILaunchConfiguration configuration) throws CoreException {
		List<String> classpath = newArrayList(super.getClasspath(configuration));
//		classpath.add(pitestCommandLineJar);
		classpath.add(pitestJar);

		return classpath;
	}

	public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {

		List<String> classpathList = getClasspathList(configuration);
		return classpathList.toArray(new String[classpathList.size()]);
	}

	private class Arguments {

		private final List<String> args = new ArrayList<>();

		Arguments add(String attribute, String value) {
			args.add(checkNotNull(attribute));
			args.add(checkNotNull(value));
			return this;
		}

		Arguments add(String attribute, Collection<String> values) {
			return add(attribute, on(',').join(values));
		}

		String[] toArray() {
			return args.toArray(new String[args.size()]);
		}
	}

}
