package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonObject;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.particle.ParticleBlueprintHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleEmitterHolder;
import com.yesman.akytheralabs.gui.panel.components.ComponentDetailsPanel;
import com.yesman.akytheralabs.gui.screen.ParticleEditorScreen;
import com.yesman.akytheralabs.gui.screen.ParticleSystemScreen;
import com.yesman.akytheralabs.gui.widget.particle.EmitterTrackPanel;
import net.minecraft.client.gui.screens.Screen;

/// The [McpTool]s that read and write the Details rows of the particle piece a [ParticleEditorScreen] edits, and of
/// an emitter of the particle system a [ParticleSystemScreen] edits. They answer and take what the component row
/// tools do, and write through [ComponentDetailsPanel#write].
final class McpParticleTools {
    private McpParticleTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_particle_rows",
            "Returns the Details rows of the top editor screen: of the particle piece on a particle piece screen, or of "
                + "the emitter at 'emitter' on a particle system screen, which is selected as a click on its track does. "
                + "The answer has the form get_component_rows gives: 'declaration', the piece's or the emitter's editor "
                + "JSON (an emitter's is one element of 'emitters' in the system's .json file), and 'rows', the shown "
                + "rows.",
            """
            {
              "type": "object",
              "properties": {
                "emitter": {"type": "integer", "description": "The emitter's 'index' from get_tracks. Needed on a particle system screen."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Target target = target(arguments);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(McpComponentTools.readRows(target.holder, target.declarations)));
            })
        ));

        McpTools.register(McpTool.of(
            "set_particle_rows",
            "Writes the Details rows of the particle piece, or of the emitter at 'emitter', on the top editor screen as "
                + "one undo step, the same as typing into the rows. 'declaration' and 'values' work as in "
                + "set_component_rows: 'declaration' replaces top-level keys of the declaration get_particle_rows "
                + "returns, and every shown row whose value that changes is written; 'values' maps a row key whose "
                + "'write' is 'values' to its value. An emitter's time span and keys are written with edit_tracks, not "
                + "here. The entry stays unsaved; save with run_editor_action action=save. Returns the declaration and "
                + "rows after the write, with 'unwritten' and 'dropped' as set_component_rows gives them.",
            """
            {
              "type": "object",
              "properties": {
                "emitter": {"type": "integer", "description": "The emitter's 'index' from get_tracks. Needed on a particle system screen."},
                "declaration": {"type": "object", "description": "Declaration keys to replace, e.g. {\\"type\\": \\"overlay\\", \\"blend\\": \\"additive\\"}."},
                "values": {"type": "object", "description": "Row key to value, e.g. {\\"lifetime\\": 40}."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Target target = target(arguments);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(
                    McpComponentTools.writeRows(target.details, target.holder, target.declarations, arguments, target.what)
                ));
            })
        ));
    }

    /// The holder the tools read and write on the top editor screen, shown in its Details panel: the piece, or the
    /// emitter `arguments` name, selected first.
    private static Target target(final JsonObject arguments) {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (!(screen instanceof ParticleEditorScreen) && !(screen instanceof ParticleSystemScreen)) {
            throw new IllegalStateException("The top editor screen edits no particle piece and no particle system");
        }

        ComponentDetailsPanel details = McpTools.findWidgets(screen, ComponentDetailsPanel.class).getFirst();

        if (screen instanceof ParticleEditorScreen) {
            if (!(details.getShown() instanceof ParticleBlueprintHolder piece)) throw new IllegalStateException("The Details panel does not show the particle piece");

            return new Target(details, piece, McpComponentTools.Declarations.of(ParticleBlueprintHolder.EDITOR_CODEC), "the particle piece");
        }

        if (!arguments.has("emitter")) throw new IllegalArgumentException("A particle system screen needs 'emitter'");

        EmitterTrackPanel tracks = McpTools.findWidgets(screen, EmitterTrackPanel.class).getFirst();
        int index = arguments.get("emitter").getAsInt();
        ParticleEmitterHolder emitter = tracks.emitterIndexes().get(index);

        if (emitter == null) throw new IllegalArgumentException("No emitter at index " + index + ": removed, or not listed by get_tracks");

        if (tracks.getSelected() != emitter) tracks.setSelected(emitter);
        if (!details.isShowing(emitter)) details.showComponent(emitter);

        return new Target(details, emitter, McpComponentTools.Declarations.of(ParticleEmitterHolder.CODEC), "emitter " + index);
    }

    /// One holder the tools work on.
    ///
    /// @param what names the holder in an error
    private record Target(ComponentDetailsPanel details, Object holder, McpComponentTools.Declarations declarations, String what) {
    }
}
