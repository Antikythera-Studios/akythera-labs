package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.client.api.gui.ComboIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Objects;

/// A [ScriptType]'s swatch: the shape of its [ScriptType.Container] in the pin colors of the types it is made
/// of. A capsule for a single value, a grid for a list, braces for a set, and key cells beside value bars for
/// a map.
///
/// @param color      the color of [ScriptType#base]
/// @param valueColor the color of the type a map maps to, [#color] for any other container
public record TypeIcon(ScriptType.Container container, int color, int valueColor) implements ComboIcon {
    public static TypeIcon of(ScriptType type) {
        ScriptType value = type.container() == ScriptType.Container.MAP ? Objects.requireNonNull(type.element()) : type.base();

        return new TypeIcon(type.container(), NodeColors.forType(type.base().type()), NodeColors.forType(value.type()));
    }

    @Override
    public void draw(GuiGraphicsExtractor graphics, int x, int y) {
        // The collection shapes are 8 pixels wide, centered in the capsule's box.
        int left = x + (WIDTH - 8) / 2;

        switch (container) {
            case SINGLE -> {
                // Rounded capsule: the full bar with its four corner pixels trimmed.
                graphics.fill(x + 1, y, x + WIDTH - 1, y + HEIGHT, color);
                graphics.fill(x, y + 1, x + WIDTH, y + HEIGHT - 1, color);
            }
            case LIST -> {
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        int cx = left + column * 3;
                        int cy = y + row * 3;

                        graphics.fill(cx, cy, cx + 2, cy + 2, color);
                    }
                }
            }
            case SET -> {
                int right = left + 5;

                graphics.fill(left + 1, y, left + 3, y + 1, color);
                graphics.fill(left + 1, y + 1, left + 2, y + 3, color);
                graphics.fill(left, y + 3, left + 1, y + 5, color);
                graphics.fill(left + 1, y + 5, left + 2, y + 7, color);
                graphics.fill(left + 1, y + 7, left + 3, y + 8, color);

                graphics.fill(right, y, right + 2, y + 1, color);
                graphics.fill(right + 1, y + 1, right + 2, y + 3, color);
                graphics.fill(right + 2, y + 3, right + 3, y + 5, color);
                graphics.fill(right + 1, y + 5, right + 2, y + 7, color);
                graphics.fill(right, y + 7, right + 2, y + 8, color);
            }
            case MAP -> {
                for (int row = 0; row < 3; row++) {
                    int cy = y + row * 3;

                    graphics.fill(left, cy, left + 2, cy + 2, color);
                    graphics.fill(left + 3, cy, left + 8, cy + 2, valueColor);
                }
            }
        }
    }
}
