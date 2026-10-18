package com.dat.genshinmod.item.weapon;

import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public interface RarityWeapon {
    WeaponTier getWeaponTier();

    void initializeClient(Consumer<IClientItemExtensions> consumer);
}
