/*
 * Plugin: ModoMorte Impede que o jogador reviva por um tempo configurável após a morte (padrão: 24 horas).
 * Criado por: NineNine
 * Servidor: Servidor dos Crocantes
 * Compatível com: Spigot/Paper 1.21+ e Java 21
 */

package com.nin9.modomorte24h;

import net.kyori.adventure.text.Component;
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

        // Tarefa repetitiva para verificar o tempo restante de punição
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

                            // Adventure API (Paper 1.19+)
                            p.sendActionBar(Component.text("§cRenascendo em §f" + h + "h " + m + "m"));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("ModoMorte24h habilitado com punição de " + horas + " hora(s).");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        // Define o horário de término da punição
        final long endTime = System.currentTimeMillis() + punishmentMillis;
        deathTimes.put(player.getUniqueId(), endTime);

        // Coloca o jogador em modo espectador e segue o mais próximo
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGameMode(GameMode.SPECTATOR);

            Player nearest = getNearestPlayer(player);
            if (nearest != null) {
                player.setSpectatorTarget(nearest);
                player.sendMessage(Component.text("§7Você morreu! Agora está assistindo §a" + nearest.getName() + "§7."));
            } else {
                player.sendMessage(Component.text("§7Você morreu, mas não há jogadores vivos para assistir."));
            }
        }, 1L);
    }

    private Player getNearestPlayer(Player source) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(source) && p.getGameMode() == GameMode.SURVIVAL) {
                double distance = p.getLocation().distance(source.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    private void reviver(Player p) {
        deathTimes.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);

        Location spawn = p.getWorld().getSpawnLocation();
        p.teleport(spawn);
        p.sendMessage(Component.text("§aSua punição terminou! Você renasceu e pode jogar novamente."));
    }
}
