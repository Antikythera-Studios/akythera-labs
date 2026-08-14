package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akytheralabs.akythera.itempatch.component.ReplaceAnimationsHolder;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;

/// A component that is allowed to be attached [ComponentAttachable] which is not a [PatchComponent].
///
/// Only holders implementing this show up in the [ComponentEditorPanel]'s `+` add-at-root menu.
/// A component that needs a host — [BoneSocketHolder], which binds its bone against an armature —
/// leaves it off and is reachable only through the attach menu of a [ComponentAttachableHolder]
/// listing it in `attachableTypes()`.
public interface RootAttachableComponent {
}