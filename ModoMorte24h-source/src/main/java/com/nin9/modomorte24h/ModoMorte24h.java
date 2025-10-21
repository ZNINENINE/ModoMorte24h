package com.nin9.modomorte24h;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ModoMorte24h extends JavaPlugin implements Listener {

    private final Map<UUID, Long> deathTimestamps = new HashMap<>();
    private final Map<UUID, UUID> spectatingTargets = new HashMap<>(); // store target UUID instead of Player
    private static final long DEATH_DURATION_MS = 86_400_000L; // 24h

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ModoMorte24h habilitado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("ModoMorte24h desabilitado!");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID uuid = dead.getUniqueId();

        // Slight delay to ensure death processing finished
        Bukkit.getScheduler().runTaskLater(this, () -> {
            dead.setGameMode(GameMode.SPECTATOR);
            deathTimestamps.put(uuid, System.currentTimeMillis());

            Player nearest = getNearestAlivePlayer(dead);
            if (nearest != null) {
                dead.setSpectatorTarget(nearest);
                spectatingTargets.put(uuid, nearest.getUniqueId());
                dead.sendMessage("§7Você morreu e está observando §e" + nearest.getName() + "§7.");
                dead.sendMessage("§7Use §a/trocar§7 para mudar o alvo.");
            } else {
                dead.sendMessage("§cNenhum jogador vivo encontrado. Você permanecerá morto até que alguém entre.");
            }

            // Schedule auto-revive after 24 hours (in ticks)
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (dead.isOnline() && dead.getGameMode() == GameMode.SPECTATOR) {
                        dead.setGameMode(GameMode.SURVIVAL);
                        dead.teleport(dead.getWorld().getSpawnLocation());
                        dead.sendMessage("§aVocê renasceu após 24 horas!");
                    }
                    deathTimestamps.remove(uuid);
                    spectatingTargets.remove(uuid);
                }
            }.runTaskLater(this, 20L * 60 * 60 * 24);
        }, 2L);
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // If player is within penalty time, check if there is any alive player online
        UUID uuid = event.getPlayer().getUniqueId();
        if (deathTimestamps.containsKey(uuid)) {
            long elapsed = System.currentTimeMillis() - deathTimestamps.get(uuid);
            if (elapsed < DEATH_DURATION_MS) {
                boolean hasAlive = Bukkit.getOnlinePlayers().stream()
                        .anyMatch(p -> p.getGameMode() == GameMode.SURVIVAL && !p.getUniqueId().equals(uuid));
                if (!hasAlive) {
                    event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                            "§cVocê ainda está morto e não há jogadores vivos online.\n§7Tente novamente mais tarde.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (deathTimestamps.containsKey(uuid)) {
            long elapsed = System.currentTimeMillis() - deathTimestamps.get(uuid);
            if (elapsed < DEATH_DURATION_MS) {
                player.setGameMode(GameMode.SPECTATOR);
                Player nearest = getNearestAlivePlayer(player);
                if (nearest != null) {
                    player.setSpectatorTarget(nearest);
                    player.sendMessage("§7Você ainda está morto. Observando §e" + nearest.getName() + "§7.");
                } else {
                    player.sendMessage("§cNenhum jogador vivo encontrado. Você ficará aguardando.");
                }
            } else {
                // Revive after punishment time
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(player.getWorld().getSpawnLocation());
                player.sendMessage("§aVocê renasceu! Sua penalidade de morte acabou.");
                deathTimestamps.remove(uuid);
                spectatingTargets.remove(uuid);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return false;
        Player p = (Player) sender;

        if (label.equalsIgnoreCase("trocar")) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.sendMessage("§cVocê não está no modo espectador.");
                return true;
            }

            Player next = getNextAlivePlayer(p);
            if (next == null) {
                p.sendMessage("§cNenhum outro jogador vivo encontrado.");
                return true;
            }

            p.setSpectatorTarget(next);
            spectatingTargets.put(p.getUniqueId(), next.getUniqueId());
            p.sendMessage("§7Agora observando §e" + next.getName() + "§7.");
            return true;
        }

        return false;
    }

    private Player getNearestAlivePlayer(Player dead) {
        Player nearest = null;
        double minDistSq = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(dead)) continue;
            if (p.getGameMode() == GameMode.SURVIVAL) {
                double distSq = p.getLocation().distanceSquared(dead.getLocation());
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    private Player getNextAlivePlayer(Player currentSpectator) {
        List<Player> alive = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SURVIVAL) alive.add(p);
        }
        if (alive.isEmpty()) return null;

        UUID currentTargetUuid = spectatingTargets.get(currentSpectator.getUniqueId());
        int index = 0;
        if (currentTargetUuid != null) {
            for (int i = 0; i < alive.size(); i++) {
                if (alive.get(i).getUniqueId().equals(currentTargetUuid)) {
                    index = i + 1;
                    break;
                }
            }
        }
        if (index >= alive.size()) index = 0;
        return alive.get(index);
    }
}
