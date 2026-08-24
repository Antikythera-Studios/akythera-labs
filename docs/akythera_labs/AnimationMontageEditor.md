# Animation Montage Editor

An **Animation Montage** stitches several animation clips into one timeline and
lets you carve that timeline into named **sections** you can jump between and
chain together. Montages are how you build multi-part animations that the game
triggers and steers at runtime — attack combos, reload sequences, multi-stage
abilities.

Where an [Animation Sequence](data/Animation_Sequence.md) is a single clip, a
Montage is a *sequence of clips plus a road map* for playing through them.

![The Animation Montage Editor with the segment timeline and sections tab](images/montage-editor-overview.png)

## Editor layout

```
┌───────────────────────────┬───────────────────────────────┐
│                           │  Details                      │
│   Preview                 │  (fades, flags, layer)        │
│   (live model)            │                               │
├───────────────────────────┴───────────────┬───────────────┤
│  Timeline                                  │  Tabs:        │
│  (sections · segments · notifies)          │  Sections /   │
│                                            │  Asset Browser│
│                                            │  / Notifies   │
└────────────────────────────────────────────┴───────────────┘
```

- **Preview** *(top-left)* — plays the montage on the preview model.
- **Details** *(top-right)* — the montage's overall properties.
- **Timeline** *(bottom-left)* — the section markers, the two clip lanes, and notify tracks.
- **Tabs** *(bottom-right)* — three pages: **Montage Sections**, **Asset Browser**, and **Notify Details**.

## Details panel

| Property | What it does |
|----------|--------------|
| **Fade In** | How long (seconds) the montage blends in when it starts, easing from the previous pose. `0` is an instant cut. |
| **Fade Out** | How long (seconds) the montage blends out when it ends. `0` is an instant cut. |
| **Loops** | When on, the montage restarts when it finishes instead of stopping on the last frame. |
| **Root Motion** | When on, the movement in the clips actually drives the character through the world (see [Root Motion](data/Animation_Sequence.md#about-root-motion)). The preview draws the travel path. |
| **Layer Signature** | Which **animation layer** this montage plays on. Layers let a montage affect only part of the body while another animation continues elsewhere — for example, an upper-body reload on top of a full-body run. The default layer is the base (whole body). |

## The timeline

The timeline is where you assemble the montage. Reading top to bottom, it has:

- **Sections row** — the named markers along the timeline (see below). Click a
  marker to open the **Montage Sections** tab.
- **Segment lanes (two rows)** — the animation clips laid end to end. Two lanes are
  provided so overlapping or alternating clips are easy to read. Click a segment to
  jump to the **Asset Browser**.
- **Notify tracks** — event markers, exactly like the [Animation Sequence's
  notifies](data/Animation_Sequence.md#notifies-panel). Selecting one opens the
  **Notify Details** tab.

Drag the playhead to scrub; the preview follows.

![The montage timeline showing sections above two segment lanes](images/montage-timeline.png)

### Adding clips (Segments)

Open the **Asset Browser** tab, find the animation you want, and **drag it onto
the timeline**. Dropping it inserts the clip as a new segment at that spot, and
the other segments make room. This is how you build the montage's raw motion — the
clips play back-to-back along the merged timeline.

### Sections and play order

A **section** is a named region of the timeline. Sections are what make a montage
more than a fixed playlist: at runtime the game can start at a section, and you
define **play orders** — chains that say "after this section, go to that one." This
is how a combo works: each swing is a section, and the play order links them so the
game can advance through the combo or stop partway.

The **Montage Sections** tab is where you:

- add, rename, and delete sections,
- set where each section begins, and
- build the play-order chains as stacked rows.

![The Montage Sections tab with section names and play-order chains](images/montage-sections.png)

> **Tip:** the preview can play a single section in isolation — starting playback
> from a section follows that section's chain and pauses at the end, so you can
> check one branch of a combo without playing the whole montage.

## Tabs (bottom-right)

- **Montage Sections** — manage sections and play orders (above).
- **Asset Browser** — the list of animation clips you can drag into the timeline.
- **Notify Details** — the settings for the notify event you have selected, the same
  as in the Sequence editor.

## Working with the montage

- **Select Preview Model** — *View ▸ Select Preview Model*.
- **Save** — Ctrl+S. A `*` in the title marks unsaved changes.
- **Undo / Redo** — Ctrl+Z / Ctrl+Y (including clip drops and reorders).
