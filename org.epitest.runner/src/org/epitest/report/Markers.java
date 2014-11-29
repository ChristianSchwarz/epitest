package org.epitest.report;

import static org.pitest.mutationtest.DetectionStatus.KILLED;
import static org.pitest.mutationtest.DetectionStatus.NO_COVERAGE;
import static org.pitest.mutationtest.DetectionStatus.SURVIVED;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationResult;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

/**
 * Contains marker-ids that are used to highlight not-/covered and survived
 * mutations. The marker ids are used by  marker extension points that are defined in the plugin.xml.
 * 
 * @author Christian Schwarz
 *
 */
public class Markers {
	/** Id of the marker that is used to highlight covered lines */
	public static final String MARKER_COVERAGE = "org.epitest.coverage.yes";
	/** Id of the marker that is used to highlight uncovered lines */
	public static final String MARKER_NO_COVERAGE = "org.epitest.coverage.no";
	/** Id of the marker that is used to highlight survived mutations and as problem marker */
	public static final String MARKER_SURVIVED = "org.epitest.mutationmarker";

	private Markers() {
	}
	

	public static void createMarkersAtLine(final SourceFile sourceFile, List<MutationResult> mutationResults , Integer lineNumber) throws CoreException {
		
		if (markSurvived(sourceFile, mutationResults, lineNumber))
			return;
		
		if (markNoCoverage(sourceFile, mutationResults, lineNumber))
			return;
		
		markKilled(sourceFile, mutationResults, lineNumber);
	}

	private static boolean markKilled(final SourceFile sourceFile, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		boolean killed = contains(mutationResults, KILLED);
		if (killed)
			sourceFile.createTextMarker(MARKER_COVERAGE, lineNumber);

		return killed;
	}

	private static boolean markNoCoverage(final SourceFile sourceFile, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		boolean hasNoCoverage = contains(mutationResults, NO_COVERAGE);
		if (hasNoCoverage)
			sourceFile.createTextMarker(MARKER_NO_COVERAGE, lineNumber);

		return hasNoCoverage;
	}

	private static boolean markSurvived(final SourceFile sourceFile, List<MutationResult> mutationResults, Integer lineNumber) throws CoreException {
		Collection<MutationResult> lineMutations = filter(mutationResults, SURVIVED);
		if (lineMutations.isEmpty())
			return false;

		for (MutationResult r : lineMutations) {
			String mutationDescription = r.getDetails().getDescription();
			sourceFile.createProblemMarker(MARKER_SURVIVED, lineNumber, mutationDescription);
		}
		sourceFile.createTextMarker(MARKER_SURVIVED, lineNumber);
		return true;
	}
	
	private static Collection<MutationResult> filter(List<MutationResult> m, DetectionStatus status) {
		return Collections2.filter(m, hasStatus(status));
		
	}

	private static boolean contains(List<MutationResult> m, DetectionStatus status) {
		return Iterables.contains(m, hasStatus(status));
		
	}
	private static Predicate<MutationResult> hasStatus(final DetectionStatus status) {
		return new Predicate<MutationResult>() {

			@Override
			public boolean apply(MutationResult r) {
				return r.getStatus() == status;
			}
			
		};
	}
}
