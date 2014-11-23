package org.epitest.report;

/**
 * Contains marker-ids that are used to highlight not-/covered and survived
 * mutations. The marker ids are used by  marker extension points that are defined in the plugin.xml.
 * 
 * @author Christian Schwarz
 *
 */
public class Markers {
	/** Id of the marker that is used to highlight covered lines */
	public static final String COVERAGE = "org.epitest.coverage.yes";
	/** Id of the marker that is used to highlight uncovered lines */
	public static final String NO_COVERAGE = "org.epitest.coverage.no";
	/** Id of the marker that is used to highlight survived mutations and as problem marker */
	public static final String SURVIVED = "org.epitest.mutationmarker";

	private Markers() {
	}
}
