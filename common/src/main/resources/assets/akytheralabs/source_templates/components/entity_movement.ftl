<#--
  Constructor expression for an "akythera:entity_movement" ComponentBlueprint (EntityMovementComponentBlueprint).
  DTO: { body_rotation, can_float_in_water, enabled } — body_rotation is the EntityMovementComponent.BodyRotation constant name.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new EntityMovementComponentBlueprint(EntityMovementComponent.BodyRotation.${body_rotation}, ${can_float_in_water?c}, ${enabled?c})<#t>
