package org.epitest.launcher.ui;

import static java.util.Collections.emptyList;
import static org.eclipse.jdt.core.IJavaElement.CLASS_FILE;
import static org.eclipse.jdt.core.IJavaElement.COMPILATION_UNIT;
import static org.eclipse.jdt.core.IJavaElement.JAVA_PROJECT;
import static org.eclipse.jdt.core.IJavaElement.METHOD;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT;
import static org.eclipse.jdt.core.IJavaElement.PACKAGE_FRAGMENT_ROOT;
import static org.eclipse.jdt.core.IJavaElement.TYPE;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME;
import static org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME;
import static org.eclipse.jdt.ui.JavaUI.getEditorInputTypeRoot;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.epitest.launcher.LaunchConfigurationConstants;

import com.google.common.collect.ImmutableList;

public class LaunchShortcut implements ILaunchShortcut2 {

	private static final String EMPTY_STRING = ""; //$NON-NLS-1$
	public static final String TEST_CONFIGURATION = "Select a Test Configuration";
	public static final String TEST_RUN_CONFIGURATION = "Select JUnit configuration to run";
	public static final String CONFIGURATION_TYPE = "org.epitest.runner.launchConfigurationType";

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.ui.ILaunchShortcut#launch(org.eclipse.ui.IEditorPart,
	 * java.lang.String)
	 */
	public void launch(IEditorPart editor, String mode) {
		ITypeRoot element = getEditorInputTypeRoot(editor.getEditorInput());
		
		if (element == null) {
			showNoTestsFoundDialog();
		} else {
			launch(new Object[] { element }, mode);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.debug.ui.ILaunchShortcut#launch(org.eclipse.jface.viewers
	 * .ISelection, java.lang.String)
	 */
	public void launch(ISelection selection, String mode) {
		if (selection instanceof IStructuredSelection) {
			launch(((IStructuredSelection) selection).toArray(), mode);
		} else {
			showNoTestsFoundDialog();
		}
	}

	private void launch(Object[] elements, String mode) {
		try {
			IJavaElement elementToLaunch = null;

			if (elements.length == 1) {
				Object selected = elements[0];
				if (!(selected instanceof IJavaElement) && selected instanceof IAdaptable) {
					selected = ((IAdaptable) selected).getAdapter(IJavaElement.class);
				}
				if (selected instanceof IJavaElement) {
					IJavaElement element = (IJavaElement) selected;
					switch (element.getElementType()) {
					case JAVA_PROJECT:
					case PACKAGE_FRAGMENT_ROOT:
					case PACKAGE_FRAGMENT:
					case TYPE:
					case METHOD:
						elementToLaunch = element;
						break;
					case CLASS_FILE:
						elementToLaunch = ((IClassFile) element).getType();
						break;
					case COMPILATION_UNIT:
						elementToLaunch = ((ICompilationUnit) element).findPrimaryType();
						break;
					}
				}
			}
			if (elementToLaunch == null) {
				showNoTestsFoundDialog();
				return;
			}
			performLaunch(elementToLaunch, mode);
		} catch (InterruptedException | CoreException e) {
			e.printStackTrace();
		}
	}

	private void showNoTestsFoundDialog() {
		MessageDialog.openInformation(getShell(), "Pitclipse", "No tests found");
	}

	private void performLaunch(IJavaElement element, String mode) throws InterruptedException, CoreException {
		ILaunchConfigurationWorkingCopy temporary = createLaunchConfiguration(element);
		ILaunchConfiguration config = findExistingLaunchConfiguration(temporary, mode);
		if (config == null) {
			// no existing found: create a new one
			config = temporary.doSave();
		}
		DebugUITools.launch(config, mode);
	}

	private Shell getShell() {
		IWorkbench workbench = PlatformUI.getWorkbench();
		IWorkbenchWindow activeWorkbenchWindow = workbench.getActiveWorkbenchWindow();
		Shell shell = activeWorkbenchWindow.getShell();
		return shell;
	}

	private ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	/**
	 * Show a selection dialog that allows the user to choose one of the
	 * specified launch configurations. Return the chosen config, or
	 * <code>null</code> if the user cancelled the dialog.
	 * 
	 * @param configList
	 *            list of {@link ILaunchConfiguration}s
	 * @param mode
	 *            launch mode
	 * @return ILaunchConfiguration
	 * @throws InterruptedException
	 *             if cancelled by the user
	 */
	private ILaunchConfiguration chooseConfiguration(List<ILaunchConfiguration> configList, String mode) throws InterruptedException {
		IDebugModelPresentation labelProvider = DebugUITools.newDebugModelPresentation();
		ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), labelProvider);
		dialog.setElements(configList.toArray());

		dialog.setMultipleSelection(false);
		int result = dialog.open();
		if (result == Window.OK) {
			return (ILaunchConfiguration) dialog.getFirstResult();
		}
		throw new InterruptedException(); // cancelled by user
	}

	/**
	 * Returns the launch configuration type id of the launch configuration this
	 * shortcut will create. Clients can override this method to return the id
	 * of their launch configuration.
	 * 
	 * @return the launch configuration type id of the launch configuration this
	 *         shortcut will create
	 */
	protected String getLaunchConfigurationTypeId() {
		return CONFIGURATION_TYPE;
	}

	/**
	 * Creates a launch configuration working copy for the given element. The launch configuration type created will be of the type returned by {@link #getLaunchConfigurationTypeId}.
	 * The element type can only be of type {@link IJavaProject}, {@link IPackageFragmentRoot}, {@link IPackageFragment}, {@link IType} or {@link IMethod}.
	 *
	 * Clients can extend this method (should call super) to configure additional attributes on the launch configuration working copy.
	 * @param element element to launch
	 *
	 * @return a launch configuration working copy for the given element
	 * @throws CoreException if creation failed
	 */
	protected ILaunchConfigurationWorkingCopy createLaunchConfiguration(IJavaElement element) throws CoreException {
		final String testName;
		final String mainTypeQualifiedName;
		final String containerHandleId;

		switch (element.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
			case IJavaElement.PACKAGE_FRAGMENT: {
				String name= JavaElementLabels.getTextLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED);
				containerHandleId= element.getHandleIdentifier();
				mainTypeQualifiedName= EMPTY_STRING;
				testName= name.substring(name.lastIndexOf(IPath.SEPARATOR) + 1);
			}
			break;
			case IJavaElement.TYPE: {
				containerHandleId= EMPTY_STRING;
				// don't replace, fix for binary inner types
				mainTypeQualifiedName= ((IType) element).getFullyQualifiedName('.'); 
				testName= element.getElementName();
			}
			break;
			case IJavaElement.METHOD: {
				IMethod method= (IMethod) element;
				containerHandleId= EMPTY_STRING;
				mainTypeQualifiedName= method.getDeclaringType().getFullyQualifiedName('.');
				testName= method.getDeclaringType().getElementName() + '.' + method.getElementName();
			}
			break;
			default:
				throw new IllegalArgumentException("Invalid element type to create a launch configuration: " + element.getClass().getName()); //$NON-NLS-1$
		}

		

		ILaunchConfigurationType configType= getLaunchManager().getLaunchConfigurationType(getLaunchConfigurationTypeId());
		ILaunchConfigurationWorkingCopy wc= configType.newInstance(null, getLaunchManager().generateLaunchConfigurationName(testName));

		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, mainTypeQualifiedName);
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, element.getJavaProject().getElementName());
	
		wc.setAttribute(LaunchConfigurationConstants.ATTR_TEST_CONTAINER, containerHandleId);
		
		MigrationDelegate.mapResources(wc);
		
		if (element instanceof IMethod) {
			wc.setAttribute(LaunchConfigurationConstants.ATTR_TEST_METHOD_NAME, element.getElementName()); // only set for methods
		}
		return wc;
	}

	/**
	 * Returns the attribute names of the attributes that are compared when
	 * looking for an existing similar launch configuration. Clients can
	 * override and replace to customize.
	 * 
	 * @return the attribute names of the attributes that are compared
	 */
	protected String[] getAttributeNamesToCompare() {
		return new String[] { ATTR_PROJECT_NAME, ATTR_MAIN_TYPE_NAME };
		// JUnitLaunchConfigurationConstants.ATTR_TEST_METHOD_NAME };
	}

	private static boolean hasSameAttributes(ILaunchConfiguration config1, ILaunchConfiguration config2, String[] attributeToCompare) {
		try {
			for (String element : attributeToCompare) {
				String val1 = config1.getAttribute(element, EMPTY_STRING);
				String val2 = config2.getAttribute(element, EMPTY_STRING);
				if (!val1.equals(val2)) {
					return false;
				}
			}
			return true;
		} catch (CoreException e) {
			// ignore access problems here, return false
		}
		return false;
	}

	private ILaunchConfiguration findExistingLaunchConfiguration(ILaunchConfigurationWorkingCopy temporary, String mode) throws InterruptedException, CoreException {
		List<ILaunchConfiguration> candidateConfigs = findExistingLaunchConfigurations(temporary);

		// If there are no existing configs associated with the IType, create
		// one.
		// If there is exactly one config associated with the IType, return it.
		// Otherwise, if there is more than one config associated with the
		// IType, prompt the
		// user to choose one.
		int candidateCount = candidateConfigs.size();
		if (candidateCount == 0) {
			return null;
		} else if (candidateCount == 1) {
			return candidateConfigs.get(0);
		} else {
			// Prompt the user to choose a config. A null result means the user
			// cancelled the dialog, in which case this method returns null,
			// since cancelling the dialog should also cancel launching
			// anything.
			ILaunchConfiguration config = chooseConfiguration(candidateConfigs, mode);
			if (config != null) {
				return config;
			}
		}
		return null;
	}

	private List<ILaunchConfiguration> findExistingLaunchConfigurations(ILaunchConfigurationWorkingCopy temporary) throws CoreException {
		ILaunchConfigurationType configType = temporary.getType();

		ILaunchConfiguration[] configs = getLaunchManager().getLaunchConfigurations(configType);
		String[] attributeToCompare = getAttributeNamesToCompare();

		ImmutableList.Builder<ILaunchConfiguration> candidateConfigs = ImmutableList.builder();
		for (ILaunchConfiguration config : configs) {
			if (hasSameAttributes(config, temporary, attributeToCompare)) {
				candidateConfigs.add(config);
			}
		}
		return candidateConfigs.build();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
	public ILaunchConfiguration[] getLaunchConfigurations(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			if (ss.size() == 1) {
				List<ILaunchConfiguration> configs = findExistingLaunchConfigurations(ss.getFirstElement());
				return configs.toArray(new ILaunchConfiguration[configs.size()]);
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
	public ILaunchConfiguration[] getLaunchConfigurations(final IEditorPart editor) {
		final ITypeRoot element = getEditorInputTypeRoot(editor.getEditorInput());
		List<ILaunchConfiguration> configs = new ArrayList<>();
		if (element != null) {
			configs = findExistingLaunchConfigurations(element);
		}
		return configs.toArray(new ILaunchConfiguration[configs.size()]);
	}

	private List<ILaunchConfiguration> findExistingLaunchConfigurations(Object candidate) {
		if (!(candidate instanceof IJavaElement) && candidate instanceof IAdaptable) {
			candidate = ((IAdaptable) candidate).getAdapter(IJavaElement.class);
		}
		if (candidate instanceof IJavaElement) {
			IJavaElement element = (IJavaElement) candidate;
			IJavaElement elementToLaunch = null;
			try {
				switch (element.getElementType()) {
				case JAVA_PROJECT:
				case PACKAGE_FRAGMENT_ROOT:
				case PACKAGE_FRAGMENT:
				case TYPE:
				case METHOD:
					elementToLaunch = element;
					break;
				case CLASS_FILE:
					elementToLaunch = ((IClassFile) element).getType();
					break;
				case COMPILATION_UNIT:
					elementToLaunch = ((ICompilationUnit) element).findPrimaryType();
					break;
				}
				if (elementToLaunch == null) {
					return emptyList();
				}
				ILaunchConfigurationWorkingCopy workingCopy = createLaunchConfiguration(elementToLaunch);
				return findExistingLaunchConfigurations(workingCopy);
			} catch (CoreException e) {
			}
		}
		return emptyList();
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
	public IResource getLaunchableResource(ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss = (IStructuredSelection) selection;
			if (ss.size() == 1) {
				Object selected = ss.getFirstElement();
				if (!(selected instanceof IJavaElement) && selected instanceof IAdaptable) {
					selected = ((IAdaptable) selected).getAdapter(IJavaElement.class);
				}
				if (selected instanceof IJavaElement) {
					return ((IJavaElement) selected).getResource();
				}
			}
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @since 3.4
	 */
	public IResource getLaunchableResource(IEditorPart editor) {
		ITypeRoot element = getEditorInputTypeRoot(editor.getEditorInput());
		if (element != null) {
			try {
				return element.getCorrespondingResource();
			} catch (JavaModelException e) {
			}
		}
		return null;
	}

}
