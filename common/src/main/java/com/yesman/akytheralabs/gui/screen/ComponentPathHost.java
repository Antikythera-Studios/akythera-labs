package com.yesman.akytheralabs.gui.screen;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akytheralabs.gui.widget.script.ComponentGetterNodeWidget;

import java.util.SequencedMap;

/// An editor screen whose canvases may read the components of the patch it edits.
/// [ComponentGetterNodeWidget] dispatches on this for the paths it offers and the class it titles
/// itself with.
public interface ComponentPathHost {
    /// Every component path the edited patch holds, in tree order, mapped to the runtime component
    /// class that path builds. A path joins the names with [ComponentAttachable#PATH_SEPARATOR].
    SequencedMap<String, Class<? extends PatchComponent>> componentPaths();
}
