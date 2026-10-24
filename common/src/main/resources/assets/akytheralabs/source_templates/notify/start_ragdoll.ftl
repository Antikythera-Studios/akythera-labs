<#--
  Constructor expression for an "akythera:start_ragdoll" AnimationNotify.
  DTO (StartRagdollNotifyHolder#getDTO): { velocityScale, durationTicks } — the float multiplier and the duration in ticks.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new StartRagdollNotify(${velocityScale?c}F, ${durationTicks?c})<#t>