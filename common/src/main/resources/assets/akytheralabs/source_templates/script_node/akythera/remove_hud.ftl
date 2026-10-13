<#--
  akythera:remove_hud (hud) -> void; client implementation
  Renders an if statement; the flow compiler appends the closing semicolon.
--><#t>
if (${params[0]} != null && Minecraft.getInstance().isSameThread()) HudManager.getInstance().removeHud(${params[0]})<#t>