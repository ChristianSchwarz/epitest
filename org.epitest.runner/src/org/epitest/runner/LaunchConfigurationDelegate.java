package org.epitest.runner;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.VMRunnerConfiguration;

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

	private static final String MAIN_TYPE = "org.pitest.mutationtest.commandline.MutationCoverageReport";

	private String pitestCommandLineJar = Activator.getDefault().getPitestCmdLinePath();
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

		// mode = ILaunchManager.RUN_MODE;

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

		// java -cp <your classpath including pit jar and dependencies> \
		// org.pitest.mutationtest.commandline.MutationCoverageReport \
		// --reportDir \
		// --targetClasses com.your.package.tobemutated* \
		// --targetTests com.your.packge.*
		// --sourceDirs \

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

	private String[] toArray(List<String> strings) {
		return (String[]) strings.toArray(new String[strings.size()]);
	}

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

		
		
		// add program & VM arguments provided by getProgramArguments and
		// getVMArguments
		String pgmArgs = getProgramArguments(configuration);
		String vmArgs = getVMArguments(configuration);
		ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);
		vmArguments.addAll(asList(execArgs.getVMArgumentsArray()));
		programArguments.addAll(asList(execArgs.getProgramArgumentsArray()));

		programArguments.add("-version"); //$NON-NLS-1$
		programArguments.add("3"); //$NON-NLS-1$
	}
	
	public String[] getClasspath(ILaunchConfiguration configuration) throws CoreException{
		List<String> classpath = newArrayList( super.getClasspath(configuration));
		classpath.add( pitestCommandLineJar);
		classpath.add( pitestJar);
		
		return classpath.toArray(new String[0]);
	}

}
