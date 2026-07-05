package com.yesman.akytheralabs.editor.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class EditorKeyMapping {
    /// SHIFT|CTRL|ALT|SUPER — matches what [KeyEvent#modifiers] actually delivers,
    /// scrubbed of CAPS_LOCK / NUM_LOCK noise.
    private static final int MOD_MASK =
        InputConstants.MOD_SHIFT
        | InputConstants.MOD_CONTROL
        | InputConstants.MOD_ALT
        | InputConstants.MOD_SUPER;

    private final Component name;
    private final int defaultKeyCode;
    private final int defaultModifiers;

    private int keyCode;
    private int modifiers;

    public EditorKeyMapping(Component name, int defaultKeyCode, int defaultModifiers) {
        this.name = name;
        this.defaultKeyCode = defaultKeyCode;
        this.defaultModifiers = defaultModifiers & MOD_MASK;
        this.keyCode = this.defaultKeyCode;
        this.modifiers = this.defaultModifiers;
    }

    public EditorKeyMapping(Component name, int defaultKeyCode) {
        this(name, defaultKeyCode, 0);
    }

    public void reset() {
        keyCode = defaultKeyCode;
        modifiers = defaultModifiers;
    }

    public void set(int keyCode, int modifiers) {
        this.keyCode = keyCode;
        this.modifiers = modifiers & MOD_MASK;
    }

    public Component getName() {
        return name;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isDefault() {
        return keyCode == defaultKeyCode && modifiers == defaultModifiers;
    }

    public boolean matches(KeyEvent event) {
        return keyCode == event.key() && (modifiers & MOD_MASK) == (event.modifiers() & MOD_MASK);
    }

    /// "Ctrl+Shift+Z" / "F2" / "Unbound"
    public Component getDisplayName() {
        StringBuilder sb = new StringBuilder();

        if ((modifiers & InputConstants.MOD_CONTROL) != 0) sb.append("Ctrl+");
        if ((modifiers & InputConstants.MOD_SHIFT) != 0) sb.append("Shift+");
        if ((modifiers & InputConstants.MOD_ALT) != 0) sb.append("Alt+");
        if ((modifiers & InputConstants.MOD_SUPER) != 0) sb.append("Super+");

        sb.append(InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString());

        return Component.literal(sb.toString());
    }
}