<#--
  Constructor expression for an "akythera:camera" ComponentBlueprint (CameraComponentBlueprint).
  DTO (CameraComponentHolder#getDTO): { transform, match_player_yaw_to_camera, enabled }
  `transform` is rendered by transform.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new CameraComponentBlueprint(<@tr.transform transform/>, ${match_player_yaw_to_camera?c}, ${enabled?c})<#t>
