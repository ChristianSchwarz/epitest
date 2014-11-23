package org.epitest.report;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.internal.corext.refactoring.util.JavadocUtil;
import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;

public class EpitestResultListenerFactory implements MutationResultListenerFactory {

	

	@Override
	public String description() {
		return "Eclipse plugin for Pitest";
	}

	@Override
	public String name() {
		return "Epitest";
	}

	@Override
	public MutationResultListener getListener(ListenerArguments args) {
		return new ResultListener();
	}

}
