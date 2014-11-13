package org.epitest.runner;

import static com.google.common.base.Joiner.on;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Files.createTempDir;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.debug.core.ILaunchManager.RUN_MODE;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT;
import static org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ERR_UNSPECIFIED_MAIN_TYPE;
import static org.epitest.runner.LaunchConfigurationConstants.ATTR_TEST_CONTAINER;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.VMRunnerConfiguration;

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

	private static final String MAIN_TYPE = "org.pitest.mutationtest.commandline.MutationCoverageReport";

	private String pitestCommandLineJar = Activator.getDefault().getPitestCmdLinePath();
	private String pitestJar = Activator.getDefault().getPitestCorePath();

	private final String reportDir;

	public LaunchConfigurationDelegate() {
		File tempDir = createTempDir();
		tempDir.deleteOnExit();

		reportDir = tempDir.getAbsolutePath();
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

		launchWithVmRunner(configuration, RUN_MODE, launch, monitor);

		// IJavaProject javaProject = getJavaProject(configuration);
		//
		//
		// final PluginServices plugins = PluginServices.makeForContextLoader();
		// // targetClasses
		//
		// OptionsParser parser = new OptionsParser(new PluginFilter(plugins));
		//
		// ParseResult parseResult = parser.parse(programArguments.toArray(new
		// String[0]));
		//
		// if (!parseResult.isOk()) {
		// parser.printHelp();
		// System.err.println(">>>> " + parseResult.getErrorMessage().value());
		// } else {
		// final ReportOptions data = parseResult.getOptions();
		//
		// final CombinedStatistics stats = runReport(data, plugins);
		// }

	}

	// java -cp <your classpath including pit jar and dependencies> \
	// org.pitest.mutationtest.commandline.MutationCoverageReport \
	// --reportDir \
	// --targetClasses com.your.package.tobemutated* \
	// --targetTests com.your.packge.*
	// --sourceDirs \
	private void launchWithVmRunner(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		IVMRunner runner = getVMRunner(configuration, mode);

		File workingDir = verifyWorkingDirectory(configuration);
		String workingDirName = null;
		if (workingDir != null) {
			workingDirName = workingDir.getAbsolutePath();
		}

		// Environment variables
		String[] envp = getEnvironment(configuration);

		ArrayList<String> vmArguments = new ArrayList<String>();
		ArrayList<String> programArguments = new ArrayList<String>();
		collectExecutionArguments(configuration, vmArguments, programArguments);

		// VM-specific attributes
		Map<String, Object> vmAttributesMap = getVMSpecificAttributesMap(configuration);

		// Classpath
		String[] classpath = getClasspath(configuration);

		// Create VM config
		VMRunnerConfiguration runConfig = new VMRunnerConfiguration(MAIN_TYPE, classpath);

		runConfig.setVMArguments(toArray(vmArguments));
		runConfig.setProgramArguments(toArray(programArguments));
		runConfig.setEnvironment(envp);
		runConfig.setWorkingDirectory(workingDirName);
		runConfig.setVMSpecificAttributesMap(vmAttributesMap);

		// Bootpath
		String[] bootpath = getBootpath(configuration);
		runConfig.setBootClassPath(bootpath);

		// set the default source locator if required
		setDefaultSourceLocator(launch, configuration);

		// Launch the configuration - 1 unit of work
		runner.run(runConfig, launch, monitor);
	}

	private String[] toArray(List<String> list) {

		return list.toArray(new String[list.size()]);
	}

	// private ReportOptions buildManual(ArrayList<String> vmArguments,
	// List<String> classpath, IJavaProject javaProject) throws
	// JavaModelException {
	// final ReportOptions data = new ReportOptions();
	// String reportDir = ".";
	// data.setClassPathElements(classpath);
	//
	// Predicate<String> predicate = (String className) ->
	// !className.startsWith("org.pitest");
	// List<String> targetClasses = new ArrayList<>();
	//
	// // data.setCodePaths();
	// data.setTargetClasses(FCollection.map(targetClasses,
	// Glob.toGlobPredicate()));
	//
	// data.addChildJVMArgs(vmArguments);
	// data.setReportDir(reportDir);
	// data.setCodePaths(getSourceFolder(javaProject));
	// return data;
	// }

	private static List<String> getSourceFolder(IJavaProject javaProject) throws JavaModelException {

		IClasspathEntry[] classpathEntries = javaProject.getResolvedClasspath(true);
		return stream(classpathEntries)//
				.filter((IClasspathEntry entry) -> entry.getContentKind() == K_SOURCE)//
				.map((IClasspathEntry entry) -> entry.getPath().toFile().getAbsolutePath())//
				.collect(toList());

	}

	// private static CombinedStatistics runReport(final ReportOptions data,
	// PluginServices plugins) {
	//
	// final EntryPoint e = new EntryPoint();
	// final AnalysisResult result = e.execute(null, data, plugins);
	// if (result.getError().hasSome()) {
	// throw Unchecked.translateCheckedException(result.getError().value());
	// }
	// return result.getStatistics().value();
	//
	// }

	/**
	 * Collects all VM and program arguments. Implementors can modify and add
	 * arguments.
	 * 
	 * @param configuration
	 *            the configuration to collect the arguments for
	 * @param vmArguments
	 *            a {@link List} of {@link String} representing the resulting VM
	 *            arguments
	 * @param programArguments
	 *            a {@link List} of {@link String} representing the resulting
	 *            program arguments
	 * @exception CoreException
	 *                if unable to collect the execution arguments
	 */
	protected void collectExecutionArguments(ILaunchConfiguration configuration, List<String> vmArguments, List<String> programArguments) throws CoreException {

		IJavaProject javaProject = getJavaProject(configuration);
		// add program & VM arguments provided by getProgramArguments and
		// getVMArguments
		String pgmArgs = getProgramArguments(configuration);
		String vmArgs = getVMArguments(configuration);
		ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);
		vmArguments.addAll(asList(execArgs.getVMArgumentsArray()));
		programArguments.addAll(asList(execArgs.getProgramArgumentsArray()));

		programArguments.add("--targetClasses"); //$NON-NLS-1$
		programArguments.add(on(',').join(getUnitsForMutation(configuration))); //$NON-NLS-1$
		
		programArguments.add("--sourceDirs");
		programArguments.add(on(',').join(getSourceFolder(javaProject)));

		programArguments.add("--reportDir");
		programArguments.add(reportDir);

		programArguments.add("--targetTests");
		programArguments.add(on(',').join(getTestUnits(configuration)));

		programArguments.add("--verbose");
		programArguments.add("true");
	}

	
	private List<String> getUnitsForMutation(ILaunchConfiguration configuration) throws CoreException {
	IJavaProject javaProject = getJavaProject(configuration);
		String containerHandle = configuration.getAttribute(ATTR_TEST_CONTAINER, "");
		String testClassName = configuration.getAttribute(ATTR_MAIN_TYPE_NAME, "");
		if (!testClassName.isEmpty()) {
			IType t = javaProject.findType(testClassName);
			IPackageFragment packageFragment = t.getPackageFragment();
			
			return singletonList(packageFragment.getElementName()+".*");
		}
		if (!containerHandle.isEmpty()) {

			IJavaElement element = JavaCore.create(containerHandle);
			if (element == null || !element.exists())
				abort("Error input element does not exist! Container-Handle was:" + containerHandle, null, ERR_UNSPECIFIED_MAIN_TYPE);

			int elementType = element.getElementType();
			switch (elementType) {
//			 case IJavaElement.JAVA_PROJECT:
//				 javaProject.getAllPackageFragmentRoots()
				 
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
	//	IJavaProject javaProject = getJavaProject(configuration);
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
//			 case IJavaElement.JAVA_PROJECT:
//				 javaProject.getAllPackageFragmentRoots()
				 
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
		classpath.add(pitestCommandLineJar);
		classpath.add(pitestJar);

		return classpath;
	}

	public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException {

		List<String> classpathList = getClasspathList(configuration);
		return classpathList.toArray(new String[classpathList.size()]);
	}

}
