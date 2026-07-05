<#--
  akythera:play_blend_space_2d (blendSpaceId, x, y) -> AnimationDriver
  params[1] / params[2] are lazy suppliers (`() -> …`) so the blendspace samples them live.
--><#t>
AnimatorScript.instantiateBlendSpace2D(ResourceKey.create(AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY, Identifier.parse(${params[0]})), ${params[1]}, ${params[2]})<#t>