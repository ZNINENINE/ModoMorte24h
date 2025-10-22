/*
 * Plugin: ModoMorte99
 * Função: Impede que o jogador reviva por um tempo configurável após morrer (padrão: 24 horas).
 * Adiciona o comando /trocar para alternar a visão entre jogadores vivos enquanto estiver morto.
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
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
                // Nenhum jogador vivo — teleportar ao spawn
                Location spawn = player.getWorld().getSpawnLocation();
                player.teleport(spawn);
                player.sendMessage(Component.text(color(cfg.getString("mensagens.morteSemAlvo"))));
            }
        }, 1L);

        Bukkit.broadcast(Component.text(color(cfg.getString("mensagens.morteGlobal")
                .replace("%jogador%", player.getName())
                .replace("%horas%", String.valueOf(punishmentMillis / 3_600_000)))));
    }

    // Impede que o jogador morto troque de visão ou use comandos proibidos
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpectatorTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR && deathTimes.containsKey(p.getUniqueId())) {
            if (event.getCause() == TeleportCause.SPECTATE || event.getCause() == TeleportCause.PLUGIN) {
                event.setCancelled(true);
                p.sendActionBar(Component.text(color(cfg.getString("mensagens.bloqueioVisao",
                        "&cVocê não pode trocar de visão clicando. Use /trocar para alternar entre jogadores."))));
            }
        }
    }

    @EventHandler
    public void onCommandWhileDead(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.SPECTATOR && deathTimes.containsKey(p.getUniqueId())) {
            String msg = event.getMessage().toLowerCase();
            if (msg.startsWith("/tp") || msg.startsWith("/home") || msg.startsWith("/spawn")) {
                event.setCancelled(true);
                p.sendMessage(Component.text(color(cfg.getString("mensagens.bloqueioComando",
                        "&cVocê não pode usar comandos enquanto estiver morto."))));
            }
        }
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

        // === COMANDO /REVIVER ===
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

        // === NOVO COMANDO /TROCAR ===
        if (command.getName().equalsIgnoreCase("trocar")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cApenas jogadores podem usar este comando.");
                return true;
            }

            if (!deathTimes.containsKey(p.getUniqueId()) || p.getGameMode() != GameMode.SPECTATOR) {
                p.sendMessage(color("&cVocê só pode usar este comando enquanto estiver morto."));
                return true;
            }

            // Lista de jogadores vivos
            List<Player> vivos = Bukkit.getOnlinePlayers().stream()
                    .filter(alvo -> alvo.getGameMode() == GameMode.SURVIVAL)
                    .toList();

            if (vivos.isEmpty()) {
                p.sendMessage(color(cfg.getString("mensagens.morteSemAlvo",
                        "&cNenhum jogador vivo disponível para assistir.")));
                return true;
            }

            Player alvoNovo = null;

            if (args.length == 1) {
                alvoNovo = Bukkit.getPlayerExact(args[0]);
                if (alvoNovo == null || alvoNovo.getGameMode() != GameMode.SURVIVAL) {
                    p.sendMessage(color("&cJogador inválido ou não está vivo."));
                    return true;
                }
            } else {
                // Modo circular — alterna para o próximo jogador vivo
                int indexAtual = -1;
                if (p.getSpectatorTarget() instanceof Player atual) {
                    indexAtual = vivos.indexOf(atual);
                }
                int proximoIndex = (indexAtual + 1) % vivos.size();
                alvoNovo = vivos.get(proximoIndex);
            }

            p.setSpectatorTarget(alvoNovo);
            p.sendMessage(color("&eAgora você está assistindo &f" + alvoNovo.getName() + "&e."));
            return true;
        }

        return false;
    }

    private String color(String msg) {
        if (msg == null) return "";
        return msg.replace("&", "§");
    }
}
