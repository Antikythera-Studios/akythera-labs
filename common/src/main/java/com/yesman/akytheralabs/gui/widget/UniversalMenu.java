package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.gui.panel.main.ContentBrowserPanel;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/// A menu whose row layout is fixed and shared by every context that opens it. A context wires up
/// only the options it handles ([Actions]); the rest keep their position and render greyed out and
/// unpressable.
///
/// Used by [TopMenuBar] (one page per top-level menu) and by the right-click menus of [ExplorerPanel] and
/// [ContentBrowserPanel] (one page each).
public final class UniversalMenu {
    private UniversalMenu() {}

    /// One option a menu can offer, implemented by the per-menu option enums.
    public interface MenuOption {
        Component displayName();
    }

    /// A single row of a menu page. The type parameter pins a layout to one menu's option enum, so a
    /// context can only ever wire up options its own menu lays out.
    public sealed interface Row<O extends MenuOption> {}

    /// A row that runs the context's action for @param `option`.
    public record OptionRow<O extends MenuOption>(O option) implements Row<O> {}

    /// A row that unfolds into the sub-list the context registered for @param `option`
    /// (see [Actions#onChoices]).
    public record ChoiceListRow<O extends MenuOption>(O option) implements Row<O> {}

    /// A row that unfolds into a fixed sub-list of further rows.
    public record GroupRow<O extends MenuOption>(Component name, List<Row<O>> children) implements Row<O> {}

    /// One row of a context-supplied sub-list. See [Actions#onChoices].
    public record Choice(Component name, Runnable action) {}

    public static <O extends MenuOption> Actions<O> actions() {
        return new Actions<>();
    }

    /// Builds `rows` into a container of its own — the shape the right-click menus open.
    public static <O extends MenuOption> ContextMenuContainer buildPage(List<Row<O>> rows, Actions<O> actions, int pageWidth) {
        ContextMenuContainer.PageBuilder page = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(pageWidth);
        populate(page, rows, actions, pageWidth);

        return page.build();
    }

    /// Lays `rows` onto `page`, binding each option to `actions`. Sub-lists become sub-pages that
    /// unfold to the right on hover.
    public static <O extends MenuOption> void populate(
        ContextMenuContainer.PageBuilder page, List<Row<O>> rows, Actions<O> actions, int pageWidth
    ) {
        for (Row<O> row : rows) {
            switch (row) {
                case OptionRow<O>(O option) -> {
                    Handler handler = actions.handlers.get(option);
                    var entry = page.entry()
                        .message(option.displayName())
                        .predicate(() -> handler != null && handler.available().getAsBoolean());

                    if (handler != null) {
                        entry.onPress(_ -> handler.action().run());
                    }
                }
                case ChoiceListRow<O>(O option) -> {
                    List<Choice> choices = actions.choiceLists.getOrDefault(option, List.of());
                    ContextMenuContainer.PageBuilder subpage = page.entry()
                        .message(option.displayName())
                        .predicate(() -> !choices.isEmpty())
                        .subpage()
                        .pageWidth(pageWidth);

                    for (Choice choice : choices) {
                        Runnable action = choice.action();
                        subpage.entry().message(choice.name()).onPress(_ -> action.run());
                    }
                }
                case GroupRow<O>(Component name, List<Row<O>> children) -> {
                    ContextMenuContainer.PageBuilder subpage = page.entry()
                        .message(name)
                        .predicate(() -> actions.anyAvailable(children))
                        .subpage()
                        .pageWidth(pageWidth);

                    populate(subpage, children, actions, pageWidth);
                }
            }
        }
    }

    /// What one wired-up option does, and when it is offered. [#available] is re-read on every
    /// render, so a menu built once still greys its rows against the live selection.
    private record Handler(BooleanSupplier available, Runnable action) {}

    /// A context's wiring of a universal menu: the [MenuOption]s it handles, and what each does.
    /// Options left out render greyed out and reject clicks.
    public static final class Actions<O extends MenuOption> {
        private final Map<O, Handler> handlers = new HashMap<>();
        private final Map<O, List<Choice>> choiceLists = new HashMap<>();

        private Actions() {}

        /// Wires @param `option` to an action this context always offers.
        public Actions<O> on(final O option, final Runnable action) {
            return on(option, () -> true, action);
        }

        /// Wires @param `option` to an action offered only while @param `available` holds.
        public Actions<O> on(final O option, final BooleanSupplier available, final Runnable action) {
            handlers.put(option, new Handler(available, action));
            return this;
        }

        /// Fills the sub-list of @param `option` with the choices this context offers. An empty list
        /// leaves the row greyed out.
        public Actions<O> onChoices(final O option, final List<Choice> choices) {
            choiceLists.put(option, choices);
            return this;
        }

        /// Whether any of `rows` is offered — a [GroupRow]'s own availability. A plain loop rather
        /// than a stream: this runs per group row per render.
        private boolean anyAvailable(final List<Row<O>> rows) {
            for (Row<O> row : rows) {
                if (available(row)) return true;
            }

            return false;
        }

        private boolean available(final Row<O> row) {
            return switch (row) {
                case OptionRow<O>(O option) -> {
                    Handler handler = handlers.get(option);
                    yield handler != null && handler.available().getAsBoolean();
                }
                case ChoiceListRow<O>(O option) -> !choiceLists.getOrDefault(option, List.of()).isEmpty();
                case GroupRow<O> group -> anyAvailable(group.children());
            };
        }
    }
}
