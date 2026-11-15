<#--
  Constructor expression for an "akythera:aim" AnimationNotifyState.
  DTO (AimNotifyStateHolder#getDTO): { bone } — the bone that turns, { weight } — the share of the view pitch,
  { maxPitch } — the largest turn in degrees, { blendTime } — the seconds the turn eases over.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new AimNotifyState("${bone}", ${weight?c}F, ${maxPitch?c}F, ${blendTime?c}F)<#t>
