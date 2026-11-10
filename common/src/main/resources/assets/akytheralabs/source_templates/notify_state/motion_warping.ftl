<#--
  Constructor expression for an "akythera:motion_warping" AnimationNotifyState.
  DTO (MotionWarpingNotifyStateHolder#getDTO): { target } — the MotionWarpTargetBlueprint expression, { updateRate } — the int
  tick interval, { maxStretch, smooth, maxRotationRate } — floats, { warpTranslation, modifyX, modifyY, warpRotation } — booleans.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new MotionWarpingNotifyState(${target}, ${updateRate?c}, ${warpTranslation?c}, ${maxStretch?c}F, ${smooth?c}F, ${modifyX?c}, ${modifyY?c}, ${warpRotation?c}, ${maxRotationRate?c}F)<#t>