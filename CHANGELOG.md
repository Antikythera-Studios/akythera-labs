# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-SNAPSHOT.5] - 2026-10-25

- Editor support for item patch components, collider components and the ability system
- New editor screens
  - `Ability` editor screen
  - `Particle` and `ParticleSystem` editor screens (trail editing, notify previews)
  - `Widget` editor screen
  - `ArmaturePhysicsData` editor screen (rag doll bodies, bone constraints, simulation preview)
  - `ClothPhysicsData` editor screen
  - Sound asset editor and texture preview screens
- Tools > Model Skinning: auto weight, paint and pose a vanilla or baked model, export as `.akasset`
- Animation retargeter: root motion generation, templates applied to all entries
- Projects load into the world as temporary packs and reload behind one loading overlay
- Compile results persist until the next compile; failed component rows are marked red
- Copy, cut and paste work on every node graph canvas, recorded in undo history
- Followed engine changes
  - `EquipmentSlotBinderHolder`, `SkinnedArmorRendererHolder`, `RootMotionComponentHolder`
  - Merged bone drivers and `LayerSetup` of layered blend per bone
  - `face_body_to_view` and `self_render_type` source templates
  - Removed the slim mesh row
- Fixed cloth preview ignoring its component transform

## [0.2.0-SNAPSHOT.3] - 2026-08-24

- Removed resource manager mixins that returns editor accessors due to the accessor leak when closing a project
- Fixed `Entity Type => Armature` mapper popup to warn the mappings that have deleted armature aseets.
- Updated documents

## [0.2.0-SNAPSHOT.2] - 2026-08-23

- Anim graph transition properties now shows up in details panel.

## [0.2.0-SNAPSHOT.1] - 2026-08-17

### Added

- Project editor
  - Main editor screen (with world viewport)
  - `AnimationSequence` editor screen
  - `AnimationMontage` editor screen
  - `BlendSpace2D` editor screen
  - `AnimatorScript` editor screen
    - With visual scripting, like Unreal Engine's Blueprint.
  - `EntityPatch` editor screen
- Developer tools for productivity
  - Animation Retargeter
  - History based working set
    - Undo, Redo function based on work stack
  - Project exporter both in data pack and source code(.java) shape