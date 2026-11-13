<#--
  Constructor expression for an "akythera:camera" ComponentBlueprint (CameraComponentBlueprint).
  DTO (CameraComponentHolder#getDTO): { transform, match_player_yaw_to_camera, collide_with_terrain, probe_radius, enabled }
  `transform` is rendered by transform.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new CameraComponentBlueprint(<@tr.transform transform/>, ${match_player_yaw_to_camera?c}, ${collide_with_terrain?c}, ${probe_radius?c}D, ${enabled?c})<#t>
