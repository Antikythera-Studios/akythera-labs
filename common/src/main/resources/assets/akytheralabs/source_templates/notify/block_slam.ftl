<#--
  Constructor expression for an "akythera:block_slam" AnimationNotify.
  DTO (BlockSlamNotifyHolder#getDTO): { radius } — in blocks, { offsetX, offsetY, offsetZ } — the offset from the feet.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new BlockSlamNotify(${radius?c}D, new net.minecraft.world.phys.Vec3(${offsetX?c}D, ${offsetY?c}D, ${offsetZ?c}D))<#t>
