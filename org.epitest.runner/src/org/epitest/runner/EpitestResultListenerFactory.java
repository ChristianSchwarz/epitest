package org.epitest.runner;

import java.util.ArrayList;
import java.util.List;

import org.pitest.mutationtest.ClassMutationResults;
import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;

public class EpitestResultListenerFactory implements MutationResultListenerFactory {

	private List<ClassMutationResults> mutationResults = new ArrayList<>();

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

		return new MutationResultListener() {
			@Override
			public void runStart() {
			}

			@Override
			public void runEnd() {
			}

			@Override
			public void handleMutationResult(ClassMutationResults results) {
				mutationResults.add(results);
				System.err.println(results);
			}
		};
	}

}
