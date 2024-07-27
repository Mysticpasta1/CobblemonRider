package dev.zanckor.cobblemonridingfabric.mixininterface;

public interface IPokemonStamina {
    public int cobblemonRider$getStamina();
    public void cobblemonRider$setStamina(int stamina);
    public void cobblemonRider$decreaseStamina(int amount);
    public void cobblemonRider$increaseStamina(int amount);
    public int cobblemonRider$getMaxStamina();
}
