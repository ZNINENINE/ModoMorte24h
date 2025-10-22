package com.nin9.modomorte24h;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.Location;

public class ModoMorte24h extends JavaPlugin implements Listener {

    private final Map<UUID, Long> deathTimes = new HashMap<>();
    private final Map<UUID, UUID> currentSpectating = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ModoMorte24h ativado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ModoMorte24h desativado!");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        deathTimes.put(dead.getUniqueId(), System.currentTimeMillis() + 86400000);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dead.isOnline()) return;

                Player target = getNearestAlivePlayer(dead);
                if (target == null) {
                    dead.sendMessage("§cNenhum jogador vivo online. Você não pode entrar até que alguém entre no servidor.");
                    dead.kickPlayer("Nenhum jogador vivo disponível para assistir.");
                    return;
                }

                dead.setGameMode(GameMode.SPECTATOR);
                dead.setSpectatorTarget(target);
                currentSpectating.put(dead.getUniqueId(), target.getUniqueId());
                dead.sendMessage("§7Você morreu! Agora está assistindo §a" + target.getName() + "§7 por 24 horas.");
            }
        }.runTaskLater(this, 40L);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!deathTimes.containsKey(p.getUniqueId())) return;
        if (!event.getMessage().equalsIgnoreCase("/trocarcamera")) return;

        event.setCancelled(true);
        Player newTarget = getNextAlivePlayer(p);
        if (newTarget == null) {
            p.sendMessage("§cNenhum outro jogador vivo disponível para assistir.");
            return;
        }

        p.setSpectatorTarget(newTarget);
        currentSpectating.put(p.getUniqueId(), newTarget.getUniqueId());
        p.sendMessage("§7Agora você está assistindo §a" + newTarget.getName() + ".");
    }

    @EventHandler
    public void onSpectatorLeave(PlayerGameModeChangeEvent event) {
        Player p = event.getPlayer();
        if (!deathTimes.containsKey(p.getUniqueId())) return;
        if (event.getNewGameMode() == GameMode.SPECTATOR) return;

        long expire = deathTimes.getOrDefault(p.getUniqueId(), 0L);
        if (System.currentTimeMillis() < expire) {
            event.setCancelled(true);
            p.sendMessage("§cVocê ainda está no modo espectador por punição de 24h.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        currentSpectating.remove(p.getUniqueId());
    }

    private Player getNearestAlivePlayer(Player viewer) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(viewer) && p.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toList());
        if (players.isEmpty()) return null;

        Player nearest = null;
        double dist = Double.MAX_VALUE;
        for (Player target : players) {
            double d = target.getLocation().distanceSquared(viewer.getLocation());
            if (d < dist) {
                dist = d;
                nearest = target;
            }
        }
        return nearest;
    }

    private Player getNextAlivePlayer(Player viewer) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(viewer) && p.getGameMode() != GameMode.SPECTATOR)
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());

        if (players.isEmpty()) return null;

        UUID current = currentSpectating.getOrDefault(viewer.getUniqueId(), null);
        int index = 0;
        if (current != null) {
            for (int i = 0; i < players.size(); i++) {
                if (players.get(i).getUniqueId().equals(current)) {
                    index = (i + 1) % players.size();
                    break;
                }
            }
        }
        return players.get(index);
    }
}
