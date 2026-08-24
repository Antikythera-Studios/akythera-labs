package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.core.animation.keyframe.AnimationClip;

import java.nio.file.Path;
import java.util.Set;

/// Modal-internal state for one dropped animation file.
/// Not a record because the fields are mutated in place (e.g. armature swap).
public final class RetargetEntry {
    public final Path source;
    public final AnimationClip clip;
    public final Set<String> boneNames;
    public final AssetExportManager.MappingTemplate template;

    public RetargetEntry(Path source, AnimationClip clip, Set<String> boneNames, AssetExportManager.MappingTemplate template) {
        this.source = source;
        this.clip = clip;
        this.boneNames = boneNames;
        this.template = template;
    }

    public String label() {
        return source.getFileName().toString();
    }
}
