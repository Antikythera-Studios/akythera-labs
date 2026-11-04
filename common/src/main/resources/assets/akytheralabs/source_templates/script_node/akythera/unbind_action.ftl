<#--
  akythera:unbind_action (action) -> void; client implementation
  Renders an if statement; the flow compiler appends the closing semicolon.
--><#t>
if (${params[0]} != null && Minecraft.getInstance().isSameThread()) InputManager.getInstance().unbindAction(${params[0]})<#t>