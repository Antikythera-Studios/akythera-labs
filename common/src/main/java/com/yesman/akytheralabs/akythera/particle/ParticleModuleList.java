package com.yesman.akytheralabs.akythera.particle;

import java.util.ArrayList;
import java.util.List;

/// The initializer list or the updater list of an emitter: its module rows, in the order they run. One of each
/// module.
public final class ParticleModuleList {
    /// Whether this is the updater list. Otherwise the initializer list.
    private final boolean updater;

    private final List<ParticleModuleHolder> modules = new ArrayList<>();

    public ParticleModuleList(final boolean updater) {
        this.updater = updater;
    }

    public boolean isUpdater() {
        return updater;
    }

    public List<ParticleModuleHolder> modules() {
        return modules;
    }
}
