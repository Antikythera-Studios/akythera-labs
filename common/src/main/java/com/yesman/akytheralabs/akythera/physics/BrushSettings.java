package com.yesman.akytheralabs.akythera.physics;

import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntList;

/// The weight brush the Paint Tool Panel edits.
public final class BrushSettings {
    /// Lowest weight [Type#SUBTRACT] leaves on a simulated vertex; [Type#SET] and [Type#ERASE] reach an absolute 0.
    public static final float MIN_SIMULATED_WEIGHT = 0.001F;

    public enum Mode {
        /// Screen-space radius; only vertices visible from the camera
        SCREEN,
        /// World-space sphere around the surface hit; visibility ignored
        VOLUME,
        /// A line dragged across the screen, full strength at its start and none at its end; visibility ignored
        GRADATION
    }

    public enum Type {
        SET,
        ADD,
        SUBTRACT,
        /// Pulls the weights under the brush toward their average; weight-0 vertices are left out
        SMOOTH,
        /// Subtracts down to an absolute 0, which takes the vertex out of the simulation
        ERASE
    }

    @EditorEditable(label = LangKeys.GUI_CLOTH_BRUSH_MODE, order = 0)
    private Mode mode = Mode.SCREEN;

    @EditorEditable(label = LangKeys.GUI_CLOTH_BRUSH_TYPE, order = 1)
    private Type type = Type.ADD;

    /// Brush radius on screen, in widget pixels
    @EditorEditable(label = LangKeys.GUI_CLOTH_BRUSH_SIZE, order = 2, min = 1.0F, max = 512.0F)
    private float size = 24.0F;

    @EditorEditable(label = LangKeys.GUI_CLOTH_BRUSH_AMOUNT, order = 3, min = 0.0F, max = 1.0F)
    private float amount = 0.1F;

    /// 0 is a hard brush, 1 fades linearly from the centre to the edge
    @EditorEditable(label = LangKeys.GUI_CLOTH_BRUSH_FALLOFF, order = 4, min = 0.0F, max = 1.0F)
    private float falloff = 0.5F;

    public Mode getMode() {
        return mode;
    }

    public Type getType() {
        return type;
    }

    public float getSize() {
        return size;
    }

    public float getAmount() {
        return amount;
    }

    public float getFalloff() {
        return falloff;
    }

    /// Brush strength at `normalizedDistance` (0 centre, 1 edge).
    public float strengthAt(final float normalizedDistance) {
        float inner = 1.0F - falloff;

        if (normalizedDistance <= inner) return 1.0F;

        return Math.max(0.0F, 1.0F - (normalizedDistance - inner) / Math.max(falloff, 1.0E-6F));
    }

    /// Paints one dab: `strengths[k]` of the brush lands on `working[indices[k]]`.
    public void paint(final float[] working, final IntList indices, final FloatList strengths) {
        float average = 0.0F;

        if (type == Type.SMOOTH) {
            float weightSum = 0.0F;
            float strengthSum = 0.0F;

            for (int k = 0; k < indices.size(); k++) {
                float weight = working[indices.getInt(k)];

                if (weight == 0.0F) continue;

                weightSum += weight * strengths.getFloat(k);
                strengthSum += strengths.getFloat(k);
            }

            if (strengthSum <= 0.0F) return;

            average = weightSum / strengthSum;
        }

        for (int k = 0; k < indices.size(); k++) {
            int i = indices.getInt(k);
            float weight = working[i];
            float strength = strengths.getFloat(k);

            working[i] = switch (type) {
                case SET -> mode == Mode.GRADATION
                    ? amount * strength : strength >= 1.0F
                        ? amount : weight + (amount - weight) * strength;
                case ADD -> Math.min(1.0F, weight + amount * strength);
                case SUBTRACT -> weight > 0.0F ? Math.max(MIN_SIMULATED_WEIGHT, weight - amount * strength) : 0.0F;
                case SMOOTH -> weight == 0.0F ? 0.0F : weight + (average - weight) * amount * strength;
                case ERASE -> Math.max(0.0F, weight - amount * strength);
            };
        }
    }
}
