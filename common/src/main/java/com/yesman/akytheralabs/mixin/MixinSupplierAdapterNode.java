package com.yesman.akytheralabs.mixin;

import com.yesman.akythera.api.data.modularize.node.ExecutionRecord;
import com.yesman.akythera.api.data.modularize.node.ScriptNode;
import com.yesman.akythera.api.data.modularize.node.SupplierAdapterNode;
import com.yesman.akythera.core.reflection.DataDrivenScript;
import com.yesman.akytheralabs.editor.debug.LiveScriptRecords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/// Records what a deferred input answers each time the driver samples it.
///
/// The node's own supplier reuses the [ScriptNode.ExecutionContext] it was built with, which belongs
/// to `construct` and stopped recording when `construct` returned. The supplier installed here runs
/// the child against a context built per sample instead, around whatever record the node's canvas is
/// collecting into at that moment — so a session started after the driver tree was assembled still
/// sees the value.
@Mixin(SupplierAdapterNode.class)
public abstract class MixinSupplierAdapterNode {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void akytheralabs$sampleIntoCanvasRecord(
        DataDrivenScript<?, ?> script, ScriptNode.ExecutionContext context, CallbackInfoReturnable<Object> cir
    ) {
        SupplierAdapterNode self = (SupplierAdapterNode)(Object)this;

        cir.setReturnValue((Supplier<Object>) () -> {
            ExecutionRecord live = LiveScriptRecords.of(script, self.canvas());

            return self.child().execute(
                script, live == null ? context : new ScriptNode.ExecutionContext(context.locals(), live));
        });
    }
}
