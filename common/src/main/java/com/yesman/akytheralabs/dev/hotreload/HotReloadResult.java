package com.yesman.akytheralabs.dev.hotreload;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// What a [HotReloader#reload] did. Class lists hold binary names.
///
/// @param errors    the compile errors, `file:line: message`. Nothing was swapped when not empty
/// @param redefined the loaded classes whose new bytes the game runs now
/// @param added     the classes the game had no bytes of before
/// @param removed   the classes the source code no longer has. They stay loaded until the game restarts
/// @param refused   the changed classes left unswapped, with the reason
/// @param failure   why the swap stopped, or null. Nothing was swapped when not null
public record HotReloadResult(
    List<String> errors, List<String> redefined, List<String> added, List<String> removed, List<String> refused, @Nullable String failure
) {
    public static HotReloadResult failed(final String failure) {
        return new HotReloadResult(List.of(), List.of(), List.of(), List.of(), List.of(), failure);
    }

    public static HotReloadResult compileFailed(final List<String> errors) {
        return new HotReloadResult(errors, List.of(), List.of(), List.of(), List.of(), null);
    }

    public boolean succeeded() {
        return errors.isEmpty() && failure == null;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("succeeded", succeeded());
        if (failure != null) json.addProperty("failure", failure);
        json.add("errors", array(errors));
        json.add("redefined", array(redefined));
        json.add("added", array(added));
        json.add("removed", array(removed));
        json.add("refused", array(refused));

        return json;
    }

    private static JsonArray array(final List<String> strings) {
        JsonArray array = new JsonArray();
        strings.forEach(array::add);

        return array;
    }
}
