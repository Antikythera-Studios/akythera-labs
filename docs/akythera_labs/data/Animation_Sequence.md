# Animation Sequence Editor

An **Animation Sequence** is the most basic playable animation in Akythera. It
takes a single animation clip and wraps it with the settings the game needs to
play it: how it blends in and out, whether it loops, whether it moves the character
through the world, and any events that should fire while it plays.

Most other assets build on sequences. A Blend Space blends between them, a Montage
stitches them into chains, and an Animator Blueprint decides when to play them.

## Create Animation Sequences

You can create new animation sequences by right pressing [Animation](../assets/Animation.md)
asset.

![create_sequence.png](../images/create_sequence.png)

Or, you can press the green plus button that is next to the search box in content browser panel.

## Editor overview

![img.png](../images/anim_sequence_screen.png)

- **Preview** *(top-left)* — plays your sequence on the preview model as you edit.
- **Details** *(top-right)* — the sequence's properties.
- **Animation Track** *(bottom-left)* — a timeline where you place events along the animation.
- **Notify Details** *(bottom-right)* — the settings for the event you have selected.

## Details panel

| Property | What it does                                                                                                                                                                                                      |
|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Animation** | The animation clip this sequence plays. Click the field to open the picker and choose a clip. This is the actual motion; everything else on this screen just controls *how* it plays.                             |
| **Fade In** | How long (in seconds) the animation takes to blend *in* when it starts. With a fade-in, the character eases from whatever pose it was in into this animation instead of snapping to it. `0` means an instant cut. |
| **Fade Out** | How long (in seconds) the animation blends *out* when it ends, easing back toward whatever plays next. `0` means an instant cut.                                                                                  |
| **Loops** | When on, the animation restarts from the beginning each time it reaches the end (a walk cycle). When off, it plays once and holds on the final frame.                                                             |
| **Root Motion** | When on, the movement baked into the animation's root actually **drives the character through the world** — a lunge attack really lunges forward. When off, the character animates "in place" and stays put.      |

![The preview showing the root-motion path line during playback](images/sequence-root-motion.png)

## Animation track panel

**Notifies** are events attached to the timeline of the animation. They let the
animation *do things* at exactly the right frame — play a footstep sound as a foot
lands, spawn a particle at the peak of a swing, or signal gameplay logic that a
hit should register.

There are two kinds:

- **Notify** — a single event fired at one moment in time (a point on the timeline).
- **Notify State** — an event that lasts over a **range** of time, with a begin and
  an end (useful for "invulnerable during this window" or "weapon trail on").

![The notify timeline with a ruler, playhead, and several event markers](images/sequence-notifies.png)

Using the panel:

- The ruler across the top is measured in frames. Drag the **playhead** to scrub
  through the animation; the preview updates to that frame.
- Use **+ Track** to add another lane. Tracks are just a way to keep your events
  organized in rows — they don't change how the events behave.
- Place events along a track and drag them to reposition them in time. Selecting
  an event opens its settings in the **Notify Details** panel.

## Notify Details panel

When you select an event in the timeline, its editable fields appear here.

- A **Notify** always has a **Time** — the moment it fires.
- A **Notify State** has a **Begin** and an **End** — the window it covers.
- Beyond timing, each event type has its own settings (for example, a "Play Sound"
  event lets you choose the sound). Those fields appear here too.

## Working with the sequence

- **Select Preview Model** — *View ▸ Select Preview Model* chooses which model the
  animation previews on. Picking a clip that ships with its own model can switch
  the preview automatically.
- **Save** — Ctrl+S. The title shows a `*` while you have unsaved changes.
- **Undo / Redo** — Ctrl+Z / Ctrl+Y.
