package com.nin9.modomorte24h;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class ModoMorte24h extends JavaPlugin implements Listener {

    private final Map<UUID, Long> deathTimes = new HashMap<>();
    private final Map<UUID, UUID> currentSpectating = new HashMap<>();
    private long punishmentDuration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        punishmentDuration = config.getLong("tempoPuniçãoHoras", 24) * 3600000;

        Bukkit.getPluginManager().registerEvents(this, this);
        startReviveTask();
        getLogger().info("ModoMorte24h v1.2 ativado!");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        deathTimes.put(dead.getUniqueId(), System.currentTimeMillis() + punishmentDuration);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!dead.isOnline()) return;

                Player target = getNearestAlivePlayer(dead);
                if (target == null) {
                    dead.kickPlayer("Nenhum jogador vivo disponível para assistir.");
                    return;
                }

                dead.setGameMode(GameMode.SPECTATOR);
                dead.setSpectatorTarget(target);
                currentSpectating.put(dead.getUniqueId(), target.getUniqueId());

                String msg = getConfig().getString("mensagens.morte", "§7Você morreu! Agora está assistindo §a%player%§7 por %horas% horas.")
                        .replace("%player%", target.getName())
                        .replace("%horas%", String.valueOf(punishmentDuration / 3600000));
                dead.sendMessage(msg);

                startCountdown(dead);
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
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        long expire = deathTimes.getOrDefault(p.getUniqueId(), 0L);
        if (expire == 0) return;

        if (System.currentTimeMillis() >= expire) {
            revivePlayer(p);
        } else {
            p.setGameMode(GameMode.SPECTATOR);
            startCountdown(p);
        }
    }

    private void startReviveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : new ArrayList<>(deathTimes.keySet())) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && System.currentTimeMillis() >= deathTimes.get(id)) {
                        revivePlayer(p);
                    }
                }
            }
        }.runTaskTimer(this, 1200L, 1200L); // a cada 60s
    }

    private void revivePlayer(Player p) {
        deathTimes.remove(p.getUniqueId());
        currentSpectating.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);
        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        p.teleport(spawn);

        String msg = getConfig().getString("mensagens.revive", "§aSua punição terminou! Você renasceu e pode jogar novamente.");
        p.sendMessage(msg);
    }

    private void startCountdown(Player p) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!deathTimes.containsKey(p.getUniqueId()) || !p.isOnline()) {
                    cancel();
                    return;
                }

                long remaining = deathTimes.get(p.getUniqueId()) - System.currentTimeMillis();
                if (remaining <= 0) {
                    revivePlayer(p);
                    cancel();
                    return;
                }

                long hours = remaining / 3600000;
                long minutes = (remaining % 3600000) / 60000;
                long seconds = (remaining % 60000) / 1000;

                p.sendActionBar("§eTempo restante para reviver: §c" + hours + "h " + minutes + "m " + seconds + "s");
            }
        }.runTaskTimer(this, 0L, 100L);
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
