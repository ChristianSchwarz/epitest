package org.epitest.runner;

import static com.google.common.io.Files.copy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.google.common.io.InputSupplier;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.epitest.runner"; //$NON-NLS-1$

	// The shared instance
	private static Activator plugin;

	private String pitestCmdLinePath;

	private String pitestCorePath;
	
	private final static String PITEST_CMD_LINE_JAR ="pitest-command-line-1.1.1.jar";

	private static final String PITEST_JAR = "pitest-1.1.1.jar";


	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		Bundle bundle = getBundle();
		pitestCmdLinePath = resolve( bundle,PITEST_CMD_LINE_JAR);
		pitestCorePath = resolve( bundle,PITEST_JAR);
		
	}

	
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static Activator getDefault() {
		return plugin;
	}

	public String getPitestCmdLinePath(){
		return pitestCmdLinePath;
	}
	
	public String getPitestCorePath() {
		return pitestCorePath;
	}
	
	
	private static String resolve(final Bundle bundle,final String jarName) throws IOException {

		BundleContext bundleContext = bundle.getBundleContext();
		File bundleFile = bundleContext.getDataFile(jarName);
		if (bundleFile.isFile())
			return bundleFile.getAbsolutePath();

		InputSupplier<InputStream> from = new InputSupplier<InputStream>() {

			@Override
			public InputStream getInput() throws IOException {

				final URL pitestUrl = bundle.getResource(jarName);
				return pitestUrl.openStream();
			}
		};
		copy(from, bundleFile);

		return bundleFile.getAbsolutePath();
	}


	


}
