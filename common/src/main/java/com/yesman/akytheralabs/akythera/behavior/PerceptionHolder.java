package com.yesman.akytheralabs.akythera.behavior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.world.behavior.PerceptionSettings;
import com.yesman.akythera.core.math.physics.collider.AkytheraColliderType;
import com.yesman.akythera.core.math.physics.collider.ColliderType;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.collider.ColliderComponentHolder;
import com.yesman.akytheralabs.akythera.collider.SphereShapeHolder;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/// Editor holder for the [PerceptionSettings] of a behavior tree asset: one holder per sense, null for a sense the
/// owner does not have.
public final class PerceptionHolder {
    public static final Codec<PerceptionHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            SightHolder.EDITOR_CODEC.optionalFieldOf("sight").forGetter(ParseUtils.provideOptional(PerceptionHolder::getSight)),
            HearingHolder.EDITOR_CODEC.optionalFieldOf("hearing").forGetter(ParseUtils.provideOptional(PerceptionHolder::getHearing)),
            VibrationHolder.EDITOR_CODEC.optionalFieldOf("vibration").forGetter(ParseUtils.provideOptional(PerceptionHolder::getVibration)),
            TouchHolder.EDITOR_CODEC.optionalFieldOf("touch").forGetter(ParseUtils.provideOptional(PerceptionHolder::getTouch))
        ).apply(instance, (sight, hearing, vibration, touch) -> {
            PerceptionHolder holder = new PerceptionHolder();

            holder.sight = sight.orElse(null);
            holder.hearing = hearing.orElse(null);
            holder.vibration = vibration.orElse(null);
            holder.touch = touch.orElse(null);

            return holder;
        })
    );

    @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_SIGHT, order = 0, defaultElement = "newSight")
    private @Nullable SightHolder sight;

    @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_HEARING, order = 1, defaultElement = "newHearing")
    private @Nullable HearingHolder hearing;

    @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_VIBRATION, order = 2, defaultElement = "newVibration")
    private @Nullable VibrationHolder vibration;

    @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_TOUCH, order = 3, defaultElement = "newTouch")
    private @Nullable TouchHolder touch;

    private static SightHolder newSight() {
        return new SightHolder();
    }

    private static HearingHolder newHearing() {
        return new HearingHolder();
    }

    private static VibrationHolder newVibration() {
        return new VibrationHolder();
    }

    private static TouchHolder newTouch() {
        return new TouchHolder();
    }

    public @Nullable SightHolder getSight() {
        return sight;
    }

    public @Nullable HearingHolder getHearing() {
        return hearing;
    }

    public @Nullable VibrationHolder getVibration() {
        return vibration;
    }

    public @Nullable TouchHolder getTouch() {
        return touch;
    }

    public void readFrom(final PerceptionSettings settings) {
        sight = settings.sight().map(sense -> {
            SightHolder holder = new SightHolder();
            holder.shape.readFrom(sense.shape());
            holder.lineOfSight = sense.lineOfSight();
            return holder;
        }).orElse(null);
        hearing = settings.hearing().map(sense -> {
            HearingHolder holder = new HearingHolder();
            holder.minVolume = sense.minVolume();
            holder.radius = sense.radius();
            return holder;
        }).orElse(null);
        vibration = settings.vibration().map(_ -> new VibrationHolder()).orElse(null);
        touch = settings.touch().map(sense -> {
            TouchHolder holder = new TouchHolder();
            holder.pushCooldown = sense.pushCooldown();
            return holder;
        }).orElse(null);
    }

    public PerceptionSettings makeObject() {
        return new PerceptionSettings(
            Optional.ofNullable(sight).map(sense -> new PerceptionSettings.Sight(sense.shape.makeObject(), sense.lineOfSight)),
            Optional.ofNullable(hearing).map(sense -> new PerceptionSettings.Hearing(sense.minVolume, sense.radius)),
            Optional.ofNullable(vibration).map(_ -> new PerceptionSettings.Vibration()),
            Optional.ofNullable(touch).map(sense -> new PerceptionSettings.Touch(sense.pushCooldown))
        );
    }

    /// [PerceptionSettings.Sight]: the shape seen in, at the owner's eyes and turned by its head.
    public static final class SightHolder {
        /// The shapes a sight may take.
        private static final Set<ColliderType> SHAPES = Set.of(AkytheraColliderType.SPHERE, AkytheraColliderType.CONE);

        public static final Codec<SightHolder> EDITOR_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                ColliderComponentHolder.EDITOR_CODEC.codec().fieldOf("shape").forGetter(SightHolder::getShape),
                Codec.BOOL.optionalFieldOf("line_of_sight", true).forGetter(holder -> holder.lineOfSight)
            ).apply(instance, (shape, lineOfSight) -> {
                SightHolder holder = new SightHolder(
                    new ColliderComponentHolder(shape.getLocalTransform(), shape.getTransformInheritPolicy(), shape.getDimensions(), SHAPES));
                holder.lineOfSight = lineOfSight;
                return holder;
            })
        );

        @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_SIGHT_SHAPE, order = 0)
        private final ColliderComponentHolder shape;

        @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_LINE_OF_SIGHT, order = 1)
        private boolean lineOfSight = true;

        /// A sphere of 16 blocks.
        private SightHolder() {
            this(new ColliderComponentHolder(TransformHolder.identity(), TransformInheritPolicy.LOC_ROT_SCA, new SphereShapeHolder(16.0D), SHAPES));
        }

        private SightHolder(final ColliderComponentHolder shape) {
            this.shape = shape;
        }

        public ColliderComponentHolder getShape() {
            return shape;
        }
    }

    /// [PerceptionSettings.Hearing]: the sphere around the owner that sounds must reach.
    public static final class HearingHolder {
        public static final Codec<HearingHolder> EDITOR_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("min_volume", 0.0F).forGetter(holder -> holder.minVolume),
                Codec.FLOAT.optionalFieldOf("radius", 0.0F).forGetter(HearingHolder::getRadius)
            ).apply(instance, (minVolume, radius) -> {
                HearingHolder holder = new HearingHolder();
                holder.minVolume = minVolume;
                holder.radius = radius;
                return holder;
            })
        );

        /// The quietest heard volume, after the falloff over the distance to the sphere.
        @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_MIN_VOLUME, order = 0, min = 0.0F)
        private float minVolume;

        /// The radius of the sphere, in blocks.
        @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_HEARING_RADIUS, order = 1, min = 0.0F, max = PerceptionSettings.Hearing.MAX_RADIUS)
        private float radius;

        public float getRadius() {
            return radius;
        }
    }

    /// [PerceptionSettings.Vibration].
    public static final class VibrationHolder {
        public static final Codec<VibrationHolder> EDITOR_CODEC = MapCodec.unit(VibrationHolder::new).codec();
    }

    /// [PerceptionSettings.Touch].
    public static final class TouchHolder {
        public static final Codec<TouchHolder> EDITOR_CODEC = Codec.INT.optionalFieldOf("push_cooldown", 10)
            .xmap(pushCooldown -> {
                TouchHolder holder = new TouchHolder();
                holder.pushCooldown = pushCooldown;
                return holder;
            }, holder -> holder.pushCooldown)
            .codec();

        /// Game ticks a pushing entity is not felt again for.
        @EditorEditable(label = LangKeys.GUI_BEHAVIOR_TREE_SCREEN_ROW_PUSH_COOLDOWN, order = 0, min = 0.0F)
        private int pushCooldown = 10;
    }
}
