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
import org.bukkit.configuration.file.FileConfiguration;
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
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();

        Bukkit.getPluginManager().registerEvents(this, this);

        long horas = cfg.getLong("tempoPunicaoHoras", 24);
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
                            reviver(p, false);
                        } else {
                            long restante = end - now;
                            long h = restante / 3_600_000;
                            long m = (restante % 3_600_000) / 60_000;

                            String msg = color(cfg.getString("mensagens.tempoRestante",
                                    "&cRenascendo em &f%h%h %m%m"))
                                    .replace("%h%", String.valueOf(h))
                                    .replace("%m%", String.valueOf(m));
                            p.sendActionBar(Component.text(msg));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("ModoMorte99 habilitado com punição de " + horas + " hora(s).");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        final long endTime = System.currentTimeMillis() + punishmentMillis;
        deathTimes.put(player.getUniqueId(), endTime);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGameMode(GameMode.SPECTATOR);

            Player nearest = getNearestPlayer(player);
            if (nearest != null) {
                player.setSpectatorTarget(nearest);
                player.sendMessage(Component.text(color(cfg.getString("mensagens.morteAssistindo")
                        .replace("%jogador%", nearest.getName()))));
            } else {
                player.sendMessage(Component.text(color(cfg.getString("mensagens.morteSemAlvo"))));
            }
        }, 1L);

        Bukkit.broadcast(Component.text(color(cfg.getString("mensagens.morteGlobal")
                .replace("%jogador%", player.getName())
                .replace("%horas%", String.valueOf(punishmentMillis / 3_600_000)))));
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

    private void reviver(Player p, boolean manual) {
        deathTimes.remove(p.getUniqueId());
        p.setGameMode(GameMode.SURVIVAL);

        Location spawn = p.getWorld().getSpawnLocation();
        p.teleport(spawn);

        if (manual) {
            p.sendMessage(Component.text(color(cfg.getString("mensagens.reviverManual"))));
        } else {
            p.sendMessage(Component.text(color(cfg.getString("mensagens.reviverAuto"))));
        }

        Bukkit.broadcast(Component.text(color(cfg.getString("mensagens.reviverGlobal")
                .replace("%jogador%", p.getName()))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reviver")) {
            if (!sender.hasPermission("modomorte.reviver")) {
                sender.sendMessage(color("&cVocê não tem permissão para usar este comando."));
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage(color("&eUso correto: /reviver <jogador>"));
                return true;
            }

            Player alvo = Bukkit.getPlayerExact(args[0]);
            if (alvo == null) {
                sender.sendMessage(color("&cJogador não encontrado ou offline."));
                return true;
            }

            reviver(alvo, true);
            sender.sendMessage(color("&aVocê reviveu o jogador &f" + alvo.getName() + "&a manualmente."));
            return true;
        }
        return false;
    }

    private String color(String msg) {
        if (msg == null) return "";
        return msg.replace("&", "§");
    }
}
