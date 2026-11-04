<#--
  Constructor expression for an "akythera:living_hurt" AnimationNotifyState.
  DTO (LivingHurtNotifyStateHolder#getDTO): { socket } — the socket path, { maxTargets } — the int target cap,
  { shared, ignoreInvulnerableTime } — booleans.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new LivingHurtNotifyState("${socket}", ${maxTargets?c}, ${shared?c}, ${ignoreInvulnerableTime?c})<#t>