package dev.zanckor.cobblemonridingfabric.mixin;

import com.cobblemon.mod.common.client.render.pokemon.PokemonRenderer;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonRenderer.class)
public class PokemonRendererMixin {

    @Inject(method = "render*", at = @At("HEAD"))
    public void render(PokemonEntity pokemon, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, CallbackInfo ci) {
        if(pokemon.hasControllingPassenger()) {
            pokemon.setHeadYaw(pokemon.getBodyYaw());
        }
    }
}
