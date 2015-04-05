package org.epitest;

import static com.google.common.base.Objects.equal;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

public class Console {

	private static IConsoleManager MANAGER = ConsolePlugin.getDefault()
			.getConsoleManager();;

	private Console() {
	}

	public static MessageConsoleStream newMessageConsoleStream(
			String consoleName) {
		for (IConsole console : MANAGER.getConsoles()) {
			if (equal(console.getName(), consoleName)
					&& (console instanceof MessageConsole)) {
				return ((MessageConsole) console).newMessageStream();
			}
		}

		return null;
	}
}
