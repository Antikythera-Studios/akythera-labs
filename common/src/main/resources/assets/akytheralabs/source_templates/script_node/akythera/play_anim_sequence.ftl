<#--
  akythera:play_anim_sequence (sequenceId) -> AnimationDriver
  params[0] is the sequence id as a String expression (usually a quoted literal).
--><#t>
AnimatorScript.instantiateAnimSequence(ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, Identifier.parse(${params[0]})))<#t>