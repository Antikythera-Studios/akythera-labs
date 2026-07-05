<#--
  akythera:anim_graph_pose (layerMap) -> Pose
  Runtime: reads the "anim_graph" layer's pose, empty pose when no layer was stored.
--><#t>
(((Map) ${params[0]}).get("anim_graph") == null ? Pose.EMPTY_POSE : ((AnimationLayer) ((Map) ${params[0]}).get("anim_graph")).getPose())<#t>