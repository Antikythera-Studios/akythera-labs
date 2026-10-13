package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;

import java.util.LinkedHashMap;
import java.util.Map;

/// The graphs one widget runs, by the name of the event or binding each implements. [WidgetBlueprintHolder] lowers
/// them into [DataDrivenWidgetBlueprint#widgetEvents] and [DataDrivenWidgetBlueprint#widgetBindings] under the
/// widget's path, so they follow the widget wherever it is renamed or moved to.
public final class WidgetGraphs {
    public static final Codec<WidgetGraphs> CODEC = Codec.unboundedMap(Codec.STRING, ScriptGraphData.CODEC).xmap(WidgetGraphs::new, graphs -> graphs.graphs);

    private final Map<String, ScriptGraphData> graphs;

    public WidgetGraphs() {
        this(Map.of());
    }

    private WidgetGraphs(final Map<String, ScriptGraphData> graphs) {
        this.graphs = new LinkedHashMap<>(graphs);
    }

    /// The live map, by event or binding name.
    public Map<String, ScriptGraphData> map() {
        return graphs;
    }
}
