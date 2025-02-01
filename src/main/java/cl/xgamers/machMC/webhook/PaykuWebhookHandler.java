package cl.xgamers.machMC.webhook;

import cl.xgamers.machMC.MachMC;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

public class PaykuWebhookHandler {

    private final LuckPerms luckPerms;
    private final MachMC plugin;
    private final OkHttpClient httpClient;

    public PaykuWebhookHandler(MachMC plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.httpClient = new OkHttpClient();
    }

    public void handleWebhook(String payload) {
        try {
            JsonObject jsonPayload = JsonParser.parseString(payload).getAsJsonObject();
            String status = jsonPayload.has("status") ? jsonPayload.get("status").getAsString() : null;
            String orderId = jsonPayload.has("order") ? jsonPayload.get("order").getAsString() : null;

            if (status == null || orderId == null) {
                plugin.getLogger().warning("Webhook recibido con datos incompletos: " + payload);
                logToDiscord("⚠️ Webhook recibido con datos incompletos: " + payload);
                return;
            }

            plugin.getLogger().info("Webhook recibido - Estado: " + status + ", Pedido: " + orderId);
            logToDiscord("📬 Webhook recibido - Estado: `" + status + "`, Pedido: `" + orderId + "`");

            if ("success".equalsIgnoreCase(status)) {
                processSuccessfulPayment(orderId);
            } else if ("cancel".equalsIgnoreCase(status)) {
                plugin.getLogger().info("El pedido fue cancelado: " + orderId);
                logToDiscord("❌ Pedido cancelado: `" + orderId + "`");
            } else {
                plugin.getLogger().warning("Estado no manejado en el webhook: " + status);
                logToDiscord("⚠️ Estado desconocido en el webhook: `" + status + "`");
            }
        } catch (JsonSyntaxException e) {
            plugin.getLogger().severe("Error al parsear JSON del webhook: " + e.getMessage());
            logToDiscord("❗ Error al parsear JSON del webhook.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error procesando el webhook: " + e.getMessage());
            logToDiscord("❗ Error procesando el webhook.");
            e.printStackTrace();
        }
    }

    private void processSuccessfulPayment(String orderId) {
        if (plugin.getConfigManager() == null) {
            plugin.getLogger().severe("ConfigManager no está disponible. No se puede procesar el pedido.");
            return;
        }

        String path = "pending-payments." + orderId;

        if (!plugin.getConfigManager().getConfig().contains(path)) {
            plugin.getLogger().warning("Pedido no encontrado: " + orderId);
            logToDiscord("❗ Pedido no encontrado: `" + orderId + "`");
            return;
        }

        String playerUUID = plugin.getConfigManager().getConfig().getString(path + ".player");
        String vipRank = plugin.getConfigManager().getConfig().getString(path + ".vip-rank");

        if (playerUUID == null || vipRank == null) {
            plugin.getLogger().warning("Datos inválidos para el pedido: " + orderId);
            logToDiscord("❗ Datos inválidos para el pedido: `" + orderId + "`");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                UUID uuid = UUID.fromString(playerUUID);
                Player player = Bukkit.getPlayer(uuid);

                if (player != null && player.isOnline()) {
                    grantVIP(player, vipRank);
                } else {
                    long expirationTime = System.currentTimeMillis() + Duration.ofDays(30).toMillis();
                    plugin.getConfigManager().savePendingVIP(uuid, vipRank, expirationTime);
                    plugin.getLogger().info("VIP guardado para jugador offline: " + playerUUID);
                    logToDiscord("💾 VIP guardado para jugador offline: `" + playerUUID + "`");
                }

                plugin.getConfigManager().removePendingPayment(orderId);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("UUID inválido en el pedido " + orderId + ": " + playerUUID);
                logToDiscord("❗ UUID inválido en el pedido: `" + orderId + "`");
            }
        });
    }

    private void grantVIP(Player player, String rankName) {
        if (luckPerms == null) {
            plugin.getLogger().severe("LuckPerms no está disponible. No se puede aplicar el rango VIP.");
            logToDiscord("❗ LuckPerms no está disponible. No se puede aplicar el rango VIP.");
            return;
        }

        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        InheritanceNode node = InheritanceNode.builder(rankName)
                .expiry(Duration.ofDays(30))
                .build();

        user.data().add(node);

        luckPerms.getUserManager().saveUser(user).thenRun(() -> {
            player.sendMessage("§a¡Rango VIP " + rankName + " aplicado exitosamente!");
            plugin.getLogger().info("Rango VIP aplicado a " + player.getName() + ": " + rankName);
            logToDiscord("✅ Rango VIP `" + rankName + "` aplicado a `" + player.getName() + "`.");
        });
    }

    private void logToDiscord(String message) {
        String discordWebhookUrl = plugin.getConfigManager().getConfig().getString("discord.webhook-url");

        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            plugin.getLogger().warning("No se ha configurado un Webhook de Discord en config.yml");
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("content", message);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());

        Request request = new Request.Builder()
                .url(discordWebhookUrl)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                plugin.getLogger().warning("No se pudo enviar el mensaje al webhook de Discord: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    plugin.getLogger().info("Mensaje enviado a Discord. Código: " + response.code());
                }
            }
        });
    }
}