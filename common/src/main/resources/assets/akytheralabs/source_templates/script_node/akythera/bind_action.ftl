<#--
  akythera:bind_action (action, started, triggered, completed, canceled) -> void; client implementation
  Renders an if statement; the flow compiler appends the closing semicolon.
--><#t>
if (${params[0]} != null && Minecraft.getInstance().isSameThread()) InputManager.getInstance().bindAction(${params[0]}, ${params[1]}, ${params[2]}, ${params[3]}, ${params[4]})<#t>