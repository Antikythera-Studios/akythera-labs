package com.yesman.akytheralabs.akythera.physics.constraint;

import com.yesman.akythera.core.asset.physics.constraint.AkytheraBoneConstraintType;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraint;
import com.yesman.akythera.core.asset.physics.constraint.BoneConstraintType;
import com.yesman.akythera.core.asset.physics.constraint.TransformationConstraint;
import com.yesman.akytheralabs.akythera.physics.ArmaturePhysicsDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Editable twin of [TransformationConstraint].
public final class TransformationHolder extends BoneConstraintSettingsHolder {
    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_TARGET, order = 0, candidates = "bones", searchable = true)
    private @Nullable String target;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAP_FROM, order = 1)
    private final MapFromHolder from = new MapFromHolder();

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAP_TO, order = 2)
    private final MapToHolder to = new MapToHolder();

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_EXTRAPOLATE, order = 3)
    private boolean extrapolate;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MIX, order = 4)
    private TransformationConstraint.Mix mix = TransformationConstraint.Mix.REPLACE;

    @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SIMULATION_ONLY, order = 5)
    private boolean simulationOnly;

    private static List<String> bones(final ArmaturePhysicsDataHolder holder) {
        return holder.boneNames();
    }

    @Override
    public BoneConstraintType type() {
        return AkytheraBoneConstraintType.TRANSFORMATION;
    }

    @Override
    public void readFrom(final BoneConstraint constraint) {
        TransformationConstraint transformation = (TransformationConstraint) constraint;

        target = transformation.target();
        from.readFrom(transformation.from());
        to.readFrom(transformation.to());
        extrapolate = transformation.extrapolate();
        mix = transformation.mix();
        simulationOnly = transformation.simulationOnly();
    }

    @Override
    public TransformationConstraint makeObject() {
        return new TransformationConstraint(target == null ? "" : target, from.makeObject(), to.makeObject(), extrapolate, mix, simulationOnly);
    }

    /// Editable twin of [TransformationConstraint.MapFrom].
    public static final class MapFromHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CHANNEL, order = 0)
        private TransformationConstraint.Channel channel = TransformationConstraint.Channel.ROTATION;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_X, order = 1)
        private final RangeHolder x = new RangeHolder();

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_Y, order = 2)
        private final RangeHolder y = new RangeHolder();

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_Z, order = 3)
        private final RangeHolder z = new RangeHolder();

        public void readFrom(final TransformationConstraint.MapFrom from) {
            channel = from.channel();
            x.readFrom(from.x());
            y.readFrom(from.y());
            z.readFrom(from.z());
        }

        public TransformationConstraint.MapFrom makeObject() {
            return new TransformationConstraint.MapFrom(channel, x.makeObject(), y.makeObject(), z.makeObject());
        }
    }

    /// Editable twin of [TransformationConstraint.MapTo].
    public static final class MapToHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_CHANNEL, order = 0)
        private TransformationConstraint.Channel channel = TransformationConstraint.Channel.ROTATION;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_X, order = 1)
        private final AxisMappingHolder x = new AxisMappingHolder();

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_Y, order = 2)
        private final AxisMappingHolder y = new AxisMappingHolder();

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_AXIS_Z, order = 3)
        private final AxisMappingHolder z = new AxisMappingHolder();

        public void readFrom(final TransformationConstraint.MapTo to) {
            channel = to.channel();
            x.readFrom(to.x());
            y.readFrom(to.y());
            z.readFrom(to.z());
        }

        public TransformationConstraint.MapTo makeObject() {
            return new TransformationConstraint.MapTo(channel, x.makeObject(), y.makeObject(), z.makeObject());
        }
    }

    /// Editable twin of [TransformationConstraint.Range].
    public static final class RangeHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MIN, order = 0)
        private float min;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX, order = 1)
        private float max;

        public void readFrom(final TransformationConstraint.Range range) {
            min = range.min();
            max = range.max();
        }

        public TransformationConstraint.Range makeObject() {
            return new TransformationConstraint.Range(min, max);
        }
    }

    /// Editable twin of [TransformationConstraint.AxisMapping].
    public static final class AxisMappingHolder {
        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_SOURCE_AXIS, order = 0)
        private TransformationConstraint.@Nullable Axis source;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MIN, order = 1)
        private float min;

        @EditorEditable(label = LangKeys.GUI_ARMATURE_PHYSICS_SCREEN_WIDGET_MAX, order = 2)
        private float max;

        public void readFrom(final TransformationConstraint.AxisMapping mapping) {
            source = mapping.source();
            min = mapping.min();
            max = mapping.max();
        }

        public TransformationConstraint.AxisMapping makeObject() {
            return new TransformationConstraint.AxisMapping(source, min, max);
        }
    }
}
