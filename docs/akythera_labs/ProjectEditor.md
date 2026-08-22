# Project Editor

The **Project Editor** is your home base in Akythera Labs. It's the first thing you
see when you open a project, and it's where you browse your assets, import new
files, open the individual asset editors, and — when you're done — export the
finished project. Every other editor opens on top of this one.

The Project Editor sits on top of a **live world**, so the game keeps running
behind it and you can test your work in context.

![The Project Editor with the asset browser and a live world viewport](images/project-editor-overview.png)

## Editor layout

```
┌─────────────────────────────────────────────────────────────┐
│ Menu bar   (File · Edit · Tools · Help)                       │
├──────────────────────────────┬──────────────────────────────┤
│  Explorer                    │                               │
│  (folder tree)               │   World Viewport              │
│                              │   (the live game)             │
├──────────────────────────────┴──────────────────────────────┤
│  Content Browser                                            │
│  (assets in the selected folder)                            │
└─────────────────────────────────────────────────────────────┘
```

- **Explorer** *(top-left)* — the folder tree of your project.
- **World Viewport** *(top-right)* — the running game, so you can playtest.
- **Content Browser** *(bottom)* — the assets inside the folder you've selected.

## Explorer panel

The folder tree for your whole project. Selecting a folder shows its contents in
the Content Browser below. Each folder holds a particular kind of asset
(animations, montages, blueprints, and so on), which also decides what happens when
you drop external files into it.

The Explorer remembers which folders you had expanded and which was selected, so it
looks the same next time you open the project.

## Content Browser panel

Shows the assets inside the selected folder. This is where you **open an asset** to
edit it — doing so launches the matching editor (Sequence, Montage, Blend Space,
Blueprint, …).

**Importing files:** drag files from your computer onto the editor and they're
imported into the currently selected folder, using whatever import rules that
folder's asset type defines.

![Dragging a file into the content browser to import it](images/project-editor-import.png)

## World Viewport panel

A live view of the running game world, embedded right in the editor so you can test
changes without leaving. Resize it by dragging the panel dividers.

## Menu bar

### File

| Item | What it does |
|------|--------------|
| **Save** | Saves the whole project — every open asset, your folder layout, and the editor's panel arrangement. |
| **Export ▸ As Pack** | Exports the project as a ready-to-use pack you can drop into the game. |
| **Export ▸ As Source Code** | Exports the project as generated code, for developers who want to build on it directly. |
| **Delete** | Deletes the entire project. You'll be asked to confirm. |

### Edit

| Item | What it does |
|------|--------------|
| **Language** | Changes the editor's display language. |
| **Project Settings** | Project-wide configuration. |
| **Editor Preferences** | Your personal editor preferences. |
| **Model / Armature Mapping** | Sets which armature (skeleton) drives which model — the link that lets an animation built for one skeleton play on the right model. |

### Tools

| Item | What it does |
|------|--------------|
| **Animation Retarget** | Retargets an animation from one armature onto another, so a clip authored for one skeleton can be reused on a different one. |

### Help

Links to documentation and issue reporting.

## Closing the project

Closing the Project Editor (Esc or the menu bar's close button) always asks first,
since closing here ends your whole editing session. Choose **Save & Quit** to save
everything and exit, or **Quit Without Saving** to discard changes since your last
save.
