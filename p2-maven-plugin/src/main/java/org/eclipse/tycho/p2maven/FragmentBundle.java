package org.eclipse.tycho.p2maven;

import org.eclipse.equinox.internal.p2.metadata.InstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;

public class FragmentBundle extends InstallableUnitFragment {

	private IInstallableUnit bundleUnit;

	public FragmentBundle(IInstallableUnit bundleUnit) {
		this.bundleUnit = bundleUnit;
	}

	public IInstallableUnit getBundleUnit() {
		return bundleUnit;
	}

	@Override
	public IInstallableUnit unresolved() {
		return bundleUnit;
	}

	@Override
	public boolean isResolved() {
		return true;
	}

}
