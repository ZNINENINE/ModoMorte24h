/*
 * Plugin: ModoMorte99
 * Função: Impede que o jogador reviva por um tempo configurável após morrer (padrão: 24 horas).
 * Criado por: NineNine
 * Servidor: Servidor dos Crocantes
 * Compatível com: Spigot/Paper 1.21+ e Java 21
 */

package com.nin9.modomorte99;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.UUID;

public final class ModoMorte99 extends JavaPlugin implements Listener {

    private final HashMap<UUID, Long> deathTimes = new HashMap<>();
    private long punishmentMillis;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        long horas = getConfig().getLong("tempoPunicaoHoras", 24);
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
                            long h = restante / 3_600_000;
                            long m = (restante % 3_600_000) / 60_000;
                            p.sendActionBar(Component.text("§cRenascendo em §f" + h + "h " + m + "m"));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("ModoMorte99 habilitado com punição de " + horas + " hora(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("ModoMorte99 desativado.");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        // Define o horário de término da punição
        final long endTime = System.currentTimeMillis() + punishmentMillis;
        deathTimes.put(player.getUniqueId(), endTime);

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

        Bukkit.broadcast(Component.text("§c" + player.getName() + " morreu e ficará punido por 24h!"));
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
        Bukkit.broadcast(Component.text("§a" + p.getName() + " foi revivido e está de volta ao jogo!"));
    }

    // ===== Comando /reviver (somente admins) =====
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reviver")) {
            if (!sender.hasPermission("modomorte.reviver")) {
                sender.sendMessage("§cVocê não tem permissão para usar este comando.");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage("§eUso correto: /reviver <jogador>");
                return true;
            }

            Player alvo = Bukkit.getPlayerExact(args[0]);
            if (alvo == null) {
                sender.sendMessage("§cJogador não encontrado ou offline.");
                return true;
            }

            reviver(alvo);
            sender.sendMessage("§aVocê reviver o jogador §f" + alvo.getName() + "§a manualmente.");
            return true;
        }
        return false;
    }
}
