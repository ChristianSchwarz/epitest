package org.epitest;

import static com.google.common.io.Files.copy;
import static java.util.logging.Level.SEVERE;
import static org.eclipse.swt.SWT.COLOR_RED;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.pitest.util.Log;

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

	private final static String PITEST_CMD_LINE_JAR = "pitest-command-line-1.1.2.jar";

	private static final String PITEST_JAR = "pitest-1.1.2.jar";

	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;

		Bundle bundle = getBundle();
		pitestCmdLinePath = resolve(bundle, PITEST_CMD_LINE_JAR);
		pitestCorePath = resolve(bundle, PITEST_JAR);

		System.out.println(pitestCmdLinePath);
		System.out.println(pitestCorePath);

		Logger pitestLog = Log.getLogger();

		redirectPitestLog(pitestLog);

	}

	private void redirectPitestLog(Logger logger) {
		final MessageConsole console = new MessageConsole("epitest", null);
		final MessageConsoleStream logStream = console.newMessageStream();
		Handler handler = new Handler() {

			@Override
			public void publish(final LogRecord record) {
				
				Level level = record.getLevel();
				final int color;
				if (level == SEVERE) {
					color= COLOR_RED;;
				} else 
					color =SWT.COLOR_BLACK;

				
				final Display display = Display.getDefault();
				display.asyncExec(new Runnable() {
					
					@Override
					public void run() {
						Color c = display.getSystemColor(color);
						logStream.setColor(c);
						Throwable t = record.getThrown();
						if (t == null)
							logStream.println(record.getMessage());
						else
							t.printStackTrace(new PrintStream(logStream));
						
					}
				});
				
				
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() throws SecurityException {
			}
		};

		Handler[] handlers = logger.getHandlers();
		for (Handler h : handlers) {
			logger.removeHandler(h);
		}
		logger.addHandler(handler);
		IConsole[] consoles = {console};
		IConsoleManager cm = ConsolePlugin.getDefault().getConsoleManager();
		cm.addConsoles(consoles);
		
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

	public String getPitestCmdLinePath() {
		return pitestCmdLinePath;
	}

	public String getPitestCorePath() {
		return pitestCorePath;
	}

	private static String resolve(final Bundle bundle, final String jarName) throws IOException {

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
