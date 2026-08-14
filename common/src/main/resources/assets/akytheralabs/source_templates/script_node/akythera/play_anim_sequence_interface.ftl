<#--
  akythera:play_anim_sequence_interface (animator, interfaceKey, sequenceId) -> AnimationDriver
  params[0] is the animator expression (the construct parameter); params[1] the interface key
  expression; params[2] the sequence id as a String expression (usually a quoted literal).
--><#t>
AnimatorScript.instantiateAnimSequenceInterface(${params[0]}, ${params[1]}, ResourceKey.create(AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY, Identifier.parse(${params[2]})))<#t>