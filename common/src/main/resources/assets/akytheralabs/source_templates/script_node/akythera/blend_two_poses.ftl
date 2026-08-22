<#--
  akythera:blend_two_poses (a, b, alpha) -> AnimationDriver
  params[2] is a lazy supplier (`() -> …`) so the blend samples its alpha live.
--><#t>
new PoseBlendDriver(${params[0]}, ${params[1]}, ${params[2]})<#t>