package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// A widget that draws and feeds widgets it does not list among its children: the literal editors on the
/// nodes of a canvas, the contents of a tab panel's inactive tabs. [EditorWidgetFactory#focusUndoneWidget]
/// focuses those widgets through it.
public interface EmbeddedWidgetHost {
    /// Gives `widget` the focus a click on it would, when this host holds it, bringing it into view first.
    /// Returns whether it does.
    boolean focusEmbedded(GuiEventListener widget);

    /// Walks `roots` and their children for a host holding `widget`, lets it focus the widget, and focuses
    /// every container on the way down to that host. Returns the root the host sits under, for the caller
    /// to focus, or null when no host holds `widget`.
    static @Nullable GuiEventListener focusIn(List<? extends GuiEventListener> roots, GuiEventListener widget) {
        Map<GuiEventListener, ContainerEventHandler> parents = new IdentityHashMap<>();
        Deque<GuiEventListener> pending = new ArrayDeque<>(roots);

        while (!pending.isEmpty()) {
            GuiEventListener next = pending.pop();

            if (next instanceof EmbeddedWidgetHost host && host.focusEmbedded(widget)) {
                GuiEventListener child = next;

                for (ContainerEventHandler parent = parents.get(child); parent != null; parent = parents.get(child)) {
                    parent.setFocused(child);
                    child = parent;
                }

                return child;
            }

            if (next instanceof ContainerEventHandler container) {
                for (GuiEventListener child : container.children()) {
                    parents.put(child, container);
                    pending.add(child);
                }
            }
        }

        return null;
    }
}
