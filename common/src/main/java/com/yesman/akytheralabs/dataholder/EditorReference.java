package com.yesman.akytheralabs.dataholder;

/// Marker for an editor reference target that is a **shared mutable instance** — a
/// [ProjectObjectHolder] (a registry entry) or an editor `AssetAccessor` (an imported file).
///
/// Referrers hold the one shared instance directly, so a rename — which mutates that instance's
/// key or id in place — propagates in memory for free. What remains is the on-disk re-save of the
/// referrers, driven by [DataHolder#referencedReferences] and matched by identity (`==`).
public interface EditorReference {
}
