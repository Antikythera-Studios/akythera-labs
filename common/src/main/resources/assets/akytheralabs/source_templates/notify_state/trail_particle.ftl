<#--
  Constructor expression for an "akythera:trail_particle" AnimationNotifyState.
  DTO (TrailParticleNotifyStateHolder#getDTO): { particleSystem } — the particle system id, { socket } — the socket path.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new TrailParticleNotifyState(ResourceKey.create(AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY, Identifier.parse("${particleSystem}")), "${socket}")<#t>