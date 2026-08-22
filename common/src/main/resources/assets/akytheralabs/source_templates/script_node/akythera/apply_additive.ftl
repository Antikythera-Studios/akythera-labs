<#--
  akythera:apply_additive (base, additive, alpha) -> AnimationDriver
  params[2] is a lazy supplier (`() -> …`) so the blend samples its alpha live.
--><#t>
new AdditiveBlendDriver(${params[0]}, ${params[1]}, ${params[2]})<#t>