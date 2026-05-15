package org.yusaki.villagertradeedit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class SelectionListener implements Listener {

    private final Map<UUID, Integer> selections;

    public SelectionListener(Map<UUID, Integer> selections) {
        this.selections = selections;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }
}
