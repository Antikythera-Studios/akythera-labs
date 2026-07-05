package com.yesman.akytheralabs.dataholder;

/// Marker for an editor reference target that is a **shared mutable instance** — a
/// [ProjectObjectHolder] (a registry entry) or an editor `AssetAccessor` (an imported file).
///
/// Referrers hold the one shared instance directly, so a rename (which mutates that instance's
/// key/id in place) propagates in memory for free. The only remaining work is the on-disk re-save
/// of referrers, driven by [DataHolder#referencedReferences] and matched **by identity** (`==`).
/// This is the unified replacement for the deleted `Referenceable` / `ReferenceInfo` machinery.
public interface EditorReference {
}
