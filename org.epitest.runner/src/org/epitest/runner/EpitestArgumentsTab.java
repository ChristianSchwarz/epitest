package org.epitest.runner;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.swt.widgets.Composite;

public class EpitestArgumentsTab extends  AbstractLaunchConfigurationTab {

	@Override
	public void createControl(Composite parent) {
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute("xxs", "sxs");
	}

	@Override
	public String getName() {
		return "Epitest";
	}


}
