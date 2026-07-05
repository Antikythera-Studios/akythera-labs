package com.yesman.akytheralabs.gui.widget.script;

import net.minecraft.client.gui.Font;

/// Creates a fresh [AbstractNodeWidget] at the given graph-space position. A
/// [com.yesman.akytheralabs.gui.widget.NodeSuggestModal] entry carries one of
/// these so picking it spawns the matching node on the canvas where the wire
/// was dropped.
@FunctionalInterface
public interface NodeWidgetFactory {
    AbstractNodeWidget create(Font font, int graphX, int graphY);
}
