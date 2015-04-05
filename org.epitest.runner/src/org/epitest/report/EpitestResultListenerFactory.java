package org.epitest.report;

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
