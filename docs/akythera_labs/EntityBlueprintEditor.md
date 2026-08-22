# Entity Blueprint Editor

An **Entity Blueprint** defines custom behaviour for an entity. You attach
**components** — reusable pieces that give the entity capabilities, like an
armature to animate — and you script **events** that run at key moments in the
entity's life. It's how you turn a plain entity into one that looks and behaves
the way your addon needs.

It's built like the [Animator Blueprint](AnimatorBlueprintEditor.md), so the graph
editing works the same way — but here the graph drives *entity* logic rather than
choosing animations.

![The Entity Blueprint Editor with components, the event graph, and details](images/entity-blueprint-overview.png)

## Editor layout

```
┌──────────────┬────────────────────────────┬───────────────┐
│  Components  │                            │               │
│              │   Preview / Event Graph    │  Details      │
├──────────────┤   (tabbed)                 │  (selected    │
│  Variables   │                            │   patchComponent)  │
│              │                            │               │
└──────────────┴────────────────────────────┴───────────────┘
```

- **Components** *(top-left)* — the pieces attached to this entity.
- **Variables** *(bottom-left)* — named values your event logic uses.
- **Preview / Event Graph** *(center)* — a tab pair: a live model preview, and the
  event logic canvas.
- **Details** *(right)* — the settings for the selected patchComponent.

## Components panel

**Components** are the building blocks of the entity. Each patchComponent adds some
capability or data — for example, an **Armature** patchComponent gives the entity a
skeleton and hooks up which animator drives it.

- Add a patchComponent to the list to attach it.
- **Click a patchComponent** to load its settings into the **Details** panel on the right.
- Remove a patchComponent to detach it. (Removing then undoing restores the patchComponent
  with the settings you'd given it.)

Each patchComponent type can only be attached once — a patchComponent is identified by what
it *is*, so you configure a single copy of each.

![The Components panel with an armature patchComponent selected](images/entity-blueprint-components.png)

## Details panel

Shows the editable properties of the patchComponent you selected in the Components
panel. The exact fields depend on the patchComponent — an Armature patchComponent, for
instance, lets you choose the armature asset and the animator that drives it.
Components without editable settings yet show a placeholder.

## Center tabs: Preview and Event Graph

- **Preview** — a live 3-D view of the entity model this blueprint is for.
- **Event Graph** — the logic canvas. It comes seeded with three **entry points**,
  each of which runs at a different time:

| Event | Runs when… |
|-------|-----------|
| **On Construct** | The entity is first created/set up. Use it for one-time setup. |
| **On Join World** | The entity enters the world. Use it for anything that needs the world to exist. |
| **Update** | Every frame while the entity is active. Use it for ongoing behaviour. |

Wire nodes off each entry point to script what happens. Graph editing (adding
nodes by right-clicking or dragging off pins, wiring execution and data pins) works
exactly like the [Animator Blueprint's graphs](AnimatorBlueprintEditor.md#working-in-a-graph).

![The event graph with the three entry points](images/entity-blueprint-event-graph.png)

## Variables panel

Named values your event logic reads and writes, just like in the Animator
Blueprint. Add a variable with a name and type, then **drag it onto the Event
Graph** — you'll be asked whether to place a **Get** (read) or **Set** (write)
node. Renaming or retyping a variable updates the nodes that use it.

## File menu: Save and Compile

- **Save** (Ctrl+S) — writes the blueprint (components, event graph, and
  variables). A `*` in the title marks unsaved changes.
- **Compile** — checks that the event graph is wired correctly and that the
  blueprint assembles cleanly, reporting problems without changing your work. Run it
  before relying on the blueprint in-game.

## Other controls

- **Edit ▸ Language** / **Editor Preferences** — editor settings.
- **Undo / Redo** — Ctrl+Z / Ctrl+Y.
