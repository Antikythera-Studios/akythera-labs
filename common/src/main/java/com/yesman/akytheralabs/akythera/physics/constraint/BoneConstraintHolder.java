package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraintType;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;

import java.util.List;
import java.util.Objects;

/// Editable twin of one [BoneConstraint]: [#type] picks the [BoneConstraintType] and [#settings] holds that type's
/// own fields, swapped through [BoneConstraintSettingsHolders] when the pick changes.
public final class BoneConstraintHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CONSTRAINT_TYPE, order = 0, candidates = "typeCandidates", rebuildOnWrite = true)
    private BoneConstraintType type;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CONSTRAINT_SETTINGS, order = 1)
    private BoneConstraintSettingsHolder settings;

    public BoneConstraintHolder(final BoneConstraintSettingsHolder settings) {
        this.type = settings.type();
        this.settings = settings;
    }

    /// The holder of `constraint`. Throws when no settings holder is registered for its type.
    public static BoneConstraintHolder of(final BoneConstraint constraint) {
        BoneConstraintSettingsHolder settings = Objects.requireNonNull(
            BoneConstraintSettingsHolders.create(constraint.type()),
            () -> "No bone constraint holder registered for " + constraint.type()
        );
        settings.readFrom(constraint);

        return new BoneConstraintHolder(settings);
    }

    private List<BoneConstraintType> typeCandidates() {
        return BoneConstraintSettingsHolders.types();
    }

    public BoneConstraintType getType() {
        return type;
    }

    /// Swaps [#settings] for `type`'s holder, at that type's defaults. A type with no registered holder is ignored.
    public void setType(final BoneConstraintType type) {
        if (this.type == type) return;

        BoneConstraintSettingsHolder swapped = BoneConstraintSettingsHolders.create(type);

        if (swapped == null) return;

        this.type = type;
        this.settings = swapped;
    }

    public BoneConstraintSettingsHolder getSettings() {
        return settings;
    }

    public BoneConstraint makeObject() {
        return settings.makeObject();
    }
}
