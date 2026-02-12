package com.islandium.prison.command.base;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Classe de base pour les commandes Prison.
 */
public abstract class PrisonCommand extends AbstractCommand {

    protected final PrisonPlugin plugin;

    public PrisonCommand(@NotNull PrisonPlugin plugin, @NotNull String name, @NotNull String description) {
        super(name, description);
        this.plugin = plugin;
    }

    /**
     * Vérifie si le sender a une permission.
     */
    protected boolean hasPermission(@NotNull CommandContext ctx, @NotNull String permission) {
        if (!isPlayer(ctx)) return true; // Console has all permissions

        UUID uuid = ctx.sender().getUuid();
        PermissionsModule perms = PermissionsModule.get();

        // OP bypass
        if (perms.getGroupsForUser(uuid).contains("OP")) {
            return true;
        }

        return perms.hasPermission(uuid, permission);
    }

    /**
     * Vérifie si le sender est un joueur.
     */
    protected boolean isPlayer(@NotNull CommandContext ctx) {
        return ctx.sender() instanceof Player;
    }

    /**
     * Obtient le joueur qui exécute la commande.
     */
    @NotNull
    protected Player requirePlayer(@NotNull CommandContext ctx) {
        if (!isPlayer(ctx)) {
            throw new IllegalStateException("This command can only be executed by players");
        }
        return (Player) ctx.sender();
    }

    /**
     * Obtient le joueur qui exécute la commande (retourne null si pas un joueur).
     */
    @Nullable
    protected Player getPlayer(@NotNull CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return null;
        }
        return (Player) ctx.sender();
    }

    /**
     * Obtient l'IslandiumPlayer qui exécute la commande.
     * Peut retourner null si le joueur n'est pas trouvé dans le manager.
     */
    @NotNull
    protected IslandiumPlayer requireIslandiumPlayer(@NotNull CommandContext ctx) {
        Player player = requirePlayer(ctx);

        // Utiliser directement getCore() qui vérifie déjà l'initialisation
        return plugin.getCore().getPlayerManager().getOnlinePlayer(player.getUuid())
                .orElseThrow(() -> new IllegalStateException("Player not found in manager: " + player.getUuid()));
    }

    /**
     * Obtient l'UUID du joueur qui exécute la commande.
     * Méthode plus sûre qui ne dépend pas du PlayerManager.
     */
    @NotNull
    protected UUID getPlayerUUID(@NotNull CommandContext ctx) {
        return requirePlayer(ctx).getUuid();
    }

    /**
     * Envoie une notification visuelle (toast) au joueur.
     */
    protected void sendNotification(@NotNull CommandContext ctx, @NotNull NotificationType type, @NotNull String message) {
        if (!isPlayer(ctx)) { sendMessage(ctx, message); return; }
        NotificationUtil.send(requirePlayer(ctx), type, message);
    }

    /**
     * Envoie un message au sender.
     */
    protected void sendMessage(@NotNull CommandContext ctx, @NotNull String message) {
        ctx.sender().sendMessage(ColorUtil.parse(message));
    }

    /**
     * Envoie un message de config au sender.
     */
    protected void sendConfigMessage(@NotNull CommandContext ctx, @NotNull String key) {
        sendMessage(ctx, plugin.getConfig().getPrefixedMessage(key));
    }

    /**
     * Envoie un message de config avec placeholders au sender.
     */
    protected void sendConfigMessage(@NotNull CommandContext ctx, @NotNull String key, Object... replacements) {
        sendMessage(ctx, plugin.getConfig().getPrefixedMessage(key, replacements));
    }

    /**
     * Envoie une notification visuelle a partir d'une cle de config.
     */
    protected void sendConfigNotification(@NotNull CommandContext ctx, @NotNull NotificationType type, @NotNull String key) {
        String msg = ColorUtil.stripColors(plugin.getConfig().getPrefixedMessage(key));
        sendNotification(ctx, type, msg);
    }

    /**
     * Envoie une notification visuelle a partir d'une cle de config avec placeholders.
     */
    protected void sendConfigNotification(@NotNull CommandContext ctx, @NotNull NotificationType type, @NotNull String key, Object... replacements) {
        String msg = ColorUtil.stripColors(plugin.getConfig().getPrefixedMessage(key, replacements));
        sendNotification(ctx, type, msg);
    }

    /**
     * Complète avec succès.
     */
    protected CompletableFuture<Void> complete() {
        return CompletableFuture.completedFuture(null);
    }
}
