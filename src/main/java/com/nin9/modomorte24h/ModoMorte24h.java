package com.nin9.modomorte24h;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

public class ModoMorte24h extends JavaPlugin implements Listener {

    private final HashMap<UUID, Long> deathTimes = new HashMap<>();
    private long punishmentMillis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        long horas = getConfig().getLong("tempoPuniçãoHoras", 24);
        punishmentMillis = horas * 60 * 60 * 1000;

        Player player = event.getEntity();
        Player target = getNearestPlayer(player);
        long remainingTime = configTime;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (deathTimes.containsKey(p.getUniqueId())) {
                        long end = deathTimes.get(p.getUniqueId());
                        if (now >= end) {
                            reviver(p);
                        } else {
                            long restante = end - now;
                            long h = restante / 3600000;
                            long m = (restante % 3600000) / 60000;
                            player.sendActionBar("Renascendo em " + remainingTime + "h" + "m");
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player nearest = null;
        double distance = Double.MAX_VALUE;

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player) && other.getGameMode() == GameMode.SURVIVAL) {
                double dist = player.getLocation().distance(other.getLocation());
                if (dist < distance) {
                    distance = dist;
                    nearest = other;
                }
            }
        }

        deathTimes.put(player.getUniqueId(), System.currentTimeMillis() + punishmentMillis);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            if (nearest != null) {
                player.setSpectatorTarget(nearest);
                player.sendMessage("§7Você morreu! Agora está assistindo §a" + nearest.getName() + "§7.");
            } else {
                player.sendMessage("§7Você morreu, mas não há jogadores vivos para assistir.");
            }
        }, 1L);
    }

    private void reviver(Player p) {
        deathTimes.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);
        Location spawn = p.getWorld().getSpawnLocation();
        p.teleport(spawn);
        p.sendMessage("§aSua punição terminou! Você renasceu e pode jogar novamente.");
    }
}
