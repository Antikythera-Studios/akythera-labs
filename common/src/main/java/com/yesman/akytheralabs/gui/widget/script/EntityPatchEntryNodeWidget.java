package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.gui.widget.script.node.EntityPatchEntryNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/// A flow entry (event) node for an entity-patch blueprint graph. An [EntityPatchBlueprint] has three
/// entry points — the once-at-construction `onConstruct`, the join-world `onJoinWorld`, and the
/// per-tick `update` — all invoked with no script parameters, so each entry is a bare execution
/// origin (a single exec output).
///
/// Because all three are parameter-less, they cannot be told apart by their output labels the way the
/// animator script's Constructor / Update entries are (see [EntryPointNodeWidget]). Instead the kind is
/// persisted explicitly in the node's `op` field (see [ScriptNodeGraphWidget]'s `describe` / `rebuild`), and
/// [EntityPatchBlueprintHolder] roots each flow at the matching entry via a predicate over that marker.
public final class EntityPatchEntryNodeWidget extends AbstractNodeWidget {
    private static final int EXEC_COLOR = 0xFFEEEEEE;
    private static final int TITLE_COLOR = 0xFF1F3A5A;

    /// Which entry point this node represents. The enum name is the stable marker written to / read
    /// from the persisted `op` field, so renaming a constant is a data-breaking change.
    public enum Kind {
        ON_CONSTRUCT("On Construct"),
        ON_JOIN_WORLD("On Join World"),
        UPDATE("Update");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        public Component title() {
            return Component.literal(title);
        }
    }

    private final Kind kind;

    public EntityPatchEntryNodeWidget(Font font, Kind kind, int graphX, int graphY) {
        super(font, kind.title(), graphX, graphY);

        this.kind = kind;
        setTitleColor(TITLE_COLOR);

        // No inputs — this is a flow origin. Output: exec flow only (no event parameters).
        addOutput(new Pin("", EXEC_COLOR, true));

        relayout();
    }

    /// Which entry point this node represents.
    public Kind entryKind() {
        return kind;
    }

    /// A flow origin can never be removed — without it that entry point has no flow.
    @Override
    public boolean isDeletable() {
        return false;
    }

    @Override
    public NodeData toData() {
        return new EntityPatchEntryNodeData(kind);
    }
}
