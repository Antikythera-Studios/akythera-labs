# Animator Blueprint Editor

An **Animator Blueprint** is the *brain* that decides which animation an entity
plays and when. Instead of hard-coding "play the run animation," you build a node
graph: it reads values about the character (its speed, whether it's in the air,
which way it's moving) and picks or blends the right animation in response.

If you've used Unreal Engine's Animation Blueprint, this will feel familiar. There
are two graphs working together:

- The **Event Graph** — the *logic*. It runs every update and computes values.
- The **Anim Graph** — the *pose*. It decides the final animation, usually through
  state machines, and feeds it into a single **Output Pose**.

![The Animator Blueprint Editor with the event graph in the center](images/animator-blueprint-overview.png)

## Editor layout

```
┌──────────────┬────────────────────────────┬───────────────┐
│  Preview     │                            │  Node Details │
│  (live model)│   Blueprint Graph          │               │
├──────────────┤   (Event Graph /           ├───────────────┤
│  Variables   │    Anim Graph tabs)        │  Asset Browser│
│              │                            │  (Sequences / │
│              │                            │   Blend Spaces)│
└──────────────┴────────────────────────────┴───────────────┘
```

- **Preview** *(top-left)* — the model the blueprint drives.
- **Variables** *(bottom-left)* — the named values your logic uses.
- **Blueprint Graph** *(center)* — the node canvas, with tabs for the Event Graph,
  the Anim Graph, and any state machines you open.
- **Node Details** *(top-right)* — settings for the selected node.
- **Asset Browser** *(bottom-right)* — Sequences and Blend Spaces to drag into the
  Anim Graph.

## Working in a graph

All the graphs work the same way:

- **Add a node:** right-click empty space to open the node menu, which shows the
  actions available in that context. Or drag off a pin to see only the nodes that
  fit there.
- **Wire nodes:** drag from one node's pin to another's. **Execution** wires (the
  arrow-shaped pins) control the *order things happen*; **data** wires (the round
  pins) carry *values*.
- **Move / select / delete** nodes as you'd expect; copy-paste places the copy
  slightly offset so it doesn't land exactly on top.

![Wiring nodes on the graph canvas](images/animator-blueprint-nodes.png)

## The Event Graph

The Event Graph is the logic side. It comes seeded with two event nodes:

- **Construct** — runs once when the entity is set up. Use it for one-time
  initialization.
- **Update** — runs every frame. This is where you read the character's state and
  update your variables (for example, compute the current speed and store it).

From these events you wire flow nodes (branches, sequences), variable **Set**
nodes, and functions.

## The Anim Graph

The Anim Graph is the pose side. It always ends at one **Output Pose** node — the
final animation sent to the character. Everything you build flows *into* it from
the left.

Pose sources you can place:

- **Sequence / Blend Space players** — drag a Sequence or Blend Space from the
  **Asset Browser** onto the graph to play it directly.
- **State Machine** — a self-contained chunk of animation logic made of states and
  the transitions between them (below). This is the usual way to organize
  locomotion.
- **Cached Pose** — save a pose once and reuse it in several places. You create a
  cache on the Anim Graph and read it back with a *Use cached pose* node.
- **Anim Layer** — routes the pose onto a specific animation layer so it affects
  only part of the body.

![The Anim Graph flowing a state machine into the Output Pose](images/animator-blueprint-anim-graph.png)

## State Machines

A **State Machine** is the heart of most animator blueprints. Each **state** plays
a pose (often a blend space), and **transitions** move between states when their
conditions are met — Idle → Walk when speed rises, Walk → Jump when the character
leaves the ground, and so on.

- **Double-click a State Machine node** to open it in its own tab.
- Inside, **double-click a state** to edit the pose it plays.
- **Double-click a transition** to edit the condition that triggers it (a small
  graph that produces a true/false "can we enter?" result).
- Rename a state machine by selecting its node and editing the name in **Node
  Details**.

![A state machine with states and transition arrows](images/animator-blueprint-state-machine.png)

## Variables panel

**Variables** are your blueprint's memory — named values like `Speed`,
`IsInAir`, or `Direction` that the Event Graph writes and the Anim Graph reads.

- Add a variable and give it a name and a type.
- **Drag a variable onto a graph** to place a **Get** node (read it) or a **Set**
  node (write it).
- Renaming or retyping a variable updates the nodes that use it.

![The Variables panel listing named values](images/animator-blueprint-variables.png)

## Node Details panel

Shows the settings for whatever you have selected — for instance a State Machine's
or Cached Pose's editable **name**. When nothing special is selected it reflects
the selected variable.

## Asset Browser panel

Two tabs — **Sequences** and **Blend Spaces** — listing the animations you can
drop into the Anim Graph as player nodes. Pressing a placed Sequence Player node
previews that clip on the model.

## File menu: Save and Compile

- **Save** (Ctrl+S) — writes the blueprint. A `*` in the title marks unsaved changes.
- **Compile** — checks that your graphs are wired up correctly and can be turned
  into runnable logic, flagging any broken nodes with a red error banner. Compiling
  doesn't change your work; it's a sanity check before you rely on the blueprint
  in-game.

## Other controls

- **Edit ▸ Language** / **Editor Preferences** — editor settings.
- **Undo / Redo** — Ctrl+Z / Ctrl+Y.
- The preview model is chosen when the blueprint is created and shown in the
  Preview panel.
