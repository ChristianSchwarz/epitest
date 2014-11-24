package org.epitest.report;

import static org.eclipse.core.resources.IMarker.CHAR_END;
import static org.eclipse.core.resources.IMarker.CHAR_START;
import static org.eclipse.core.resources.IMarker.LINE_NUMBER;
import static org.eclipse.core.resources.IMarker.MESSAGE;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

final class SourceFile {
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