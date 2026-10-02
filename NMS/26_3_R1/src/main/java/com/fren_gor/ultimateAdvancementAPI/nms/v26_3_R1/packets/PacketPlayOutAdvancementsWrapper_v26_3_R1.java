package com.fren_gor.ultimateAdvancementAPI.nms.v26_3_R1.packets;

import com.fren_gor.ultimateAdvancementAPI.nms.v26_3_R1.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.packets.PacketPlayOutAdvancementsWrapper;
import com.google.common.collect.Maps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket.PositionedAdvancement;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class PacketPlayOutAdvancementsWrapper_v26_3_R1 extends PacketPlayOutAdvancementsWrapper {

    private final ClientboundUpdateAdvancementsPacket packet;

    public PacketPlayOutAdvancementsWrapper_v26_3_R1() {
        this.packet = new ClientboundUpdateAdvancementsPacket(true, Collections.emptyList(), Collections.emptySet(), Collections.emptyMap(), true);
    }

    public PacketPlayOutAdvancementsWrapper_v26_3_R1(@NotNull Map<AdvancementWrapper, Integer> toSend) {
        Map<Identifier, AdvancementProgress> map = Maps.newHashMapWithExpectedSize(toSend.size());
        // The packet carries each advancement's GUI coordinates next to its holder, so they are taken
        // from the display wrapper rather than read back from DisplayInfo.
        List<PositionedAdvancement> added = new ArrayList<>(toSend.size());
        for (Entry<AdvancementWrapper, Integer> e : toSend.entrySet()) {
            AdvancementWrapper adv = e.getKey();
            AdvancementHolder holder = (AdvancementHolder) adv.toNMS();
            map.put((Identifier) adv.getKey().toNMS(), Util.getAdvancementProgress(holder, e.getValue()));
            AdvancementDisplayWrapper display = adv.getDisplay();
            added.add(new PositionedAdvancement(holder, display.getX(), display.getY()));
        }
        this.packet = new ClientboundUpdateAdvancementsPacket(false, added, Collections.emptySet(), map, true);
    }

    public PacketPlayOutAdvancementsWrapper_v26_3_R1(@NotNull Set<MinecraftKeyWrapper> toRemove) {
        Set<Identifier> removed = new HashSet<>(toRemove.size());
        for (MinecraftKeyWrapper key : toRemove) {
            removed.add((Identifier) key.toNMS());
        }
        this.packet = new ClientboundUpdateAdvancementsPacket(false, Collections.emptyList(), removed, Collections.emptyMap(), true);
    }

    @Override
    public void sendTo(@NotNull Player player) {
        Util.sendTo(player, packet);
    }
}
