# Blend Space 2D Editor

A **Blend Space 2D** blends smoothly between several animations based on **two
inputs**. You place animations at points on a 2-D grid, and at runtime a control
point moves around that grid — driven by gameplay values like speed and turn
direction — producing a seamless mix of the nearby animations.

The classic example is locomotion: idle in the middle, walk and run further out,
strafes to the sides. As the character speeds up and changes direction, the blend
space glides between all of them with no visible pops.

![The Blend Space 2D Editor with the grid graph and a character preview](images/blendspace-editor-overview.png)

## Editor layout

```
┌───────────────────────────┬───────────────────────────────┐
│                           │  Details                      │
│   Preview                 │  (axes, ranges, smoothing)    │
│   (live model)            │                               │
├───────────────────────────┴───────────────┬───────────────┤
│  Blend Graph                               │  Tabs:        │
│  (place animations on the X/Y grid)        │  Node Details │
│                                            │  / Asset      │
│                                            │  Browser      │
└────────────────────────────────────────────┴───────────────┘
```

- **Preview** *(top-left)* — plays the currently blended result. Moving the control
  point in the graph changes what you see here in real time.
- **Details** *(top-right)* — the blend space's axes and playback settings.
- **Blend Graph** *(bottom-left)* — the 2-D grid where you place your animations.
- **Tabs** *(bottom-right)* — **Node Details** (the selected point's settings) and
  **Asset Browser** (animations to drag in).

## The Blend Graph

The graph is your blend space seen from above. The horizontal axis is your X
input, the vertical axis is your Y input, and each **node** is an animation pinned
to a specific spot.

- **Add an animation:** open the **Asset Browser** tab and **drag an animation
  onto the grid**. It becomes a node at that position.
- **Move a node:** drag it. Its position decides *when* that animation dominates
  the blend.
- **Select a node:** click it. Its exact coordinates and animation show in the
  **Node Details** tab.
- **The control point** is the cursor you move around the grid to preview the
  blend. Drop it near a node and that animation plays; drop it between nodes and
  you get a mix of them, weighted by how close it is to each.

![The blend graph with animation nodes and a control point between them](images/blendspace-graph.png)

## Details panel

The Details panel sets up the axes — the meaning and range of your two inputs —
and the overall playback.

| Property | What it does |
|----------|--------------|
| **Fade In** | How long (seconds) the blend space blends in when it starts playing. |
| **Fade Out** | How long (seconds) it blends out when it stops. |
| **Loops** | When on, the blended animation restarts when it reaches the end instead of holding. |
| **Axis Name X** | A label for the horizontal input (e.g. `Speed`). Purely for readability while authoring. |
| **Min X** / **Max X** | The lowest and highest values the horizontal input can take. This defines how wide the grid is and what the edges mean. |
| **Smooth X** | How much the horizontal input is *smoothed* before it's used. Higher values make the blend ease toward new input values instead of snapping, softening jittery inputs. `0` is no smoothing. |
| **Horizontal Division** | How many columns the grid is divided into — the vertical grid lines. Used for visual guidance and for snapping (below). |
| **Axis Name Y** | A label for the vertical input (e.g. `Direction`). |
| **Min Y** / **Max Y** | The lowest and highest values the vertical input can take. |
| **Smooth Y** | Smoothing for the vertical input, same idea as Smooth X. |
| **Vertical Division** | How many rows the grid is divided into — the horizontal grid lines. |
| **Snap to Grid** | When on, nodes snap to the nearest grid intersection as you place and move them, making it easy to keep a tidy, symmetric layout. Turning it on re-snaps every existing node. |

> Changing the **Min/Max** ranges or the **Divisions** reshapes the grid; with
> **Snap to Grid** on, your nodes re-align to the new grid automatically.

## Node Details tab

When you select a node in the graph, this tab shows:

| Field | What it does |
|-------|--------------|
| **X** | The node's horizontal position on the grid. |
| **Y** | The node's vertical position on the grid. |
| **Animation** | The animation this node contributes to the blend. This field is read-only — to change it, drag a different animation from the Asset Browser onto the node. |

![The Node Details tab showing X, Y, and the assigned animation](images/blendspace-node-details.png)

## Asset Browser tab

Lists every animation you can use — both the ones you've created in this project
and the built-in ones. Drag any of them onto the graph to add a node, or onto an
existing node to replace its animation.

## Root motion in a blend space

If the animations at the corners of a blend use [root
motion](AnimationSequenceEditor.md#about-root-motion), the preview draws the
blended travel path for the **current control-point position**, and reshapes it as
you move the cursor — so you can see how the movement itself blends, not just the
pose.

## Working with the blend space

- **Select Preview Model** — *View ▸ Select Preview Model*.
- **Save** — Ctrl+S. A `*` in the title marks unsaved changes.
- **Undo / Redo** — Ctrl+Z / Ctrl+Y.
