<#--
  akythera:play_blend_space_2d_interface (animator, interfaceKey, blendSpaceId, x, y) -> AnimationDriver
  params[0] is the animator expression (the construct parameter); params[1] the interface key
  expression; params[2] the blendspace id as a String expression; params[3] / params[4] are lazy
  suppliers (`() -> …`) so the blendspace samples them live.
--><#t>
AnimatorScript.instantiateBlendSpace2DInterface(${params[0]}, ${params[1]}, ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, Identifier.parse(${params[2]})), ${params[3]}, ${params[4]})<#t>