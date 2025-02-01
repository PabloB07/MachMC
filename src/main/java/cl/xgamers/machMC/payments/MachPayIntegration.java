package cl.xgamers.machMC.payments;

import cl.xgamers.machMC.MachMC;
import cl.xgamers.machMC.config.ConfigManager;
import okhttp3.*;
import org.bukkit.entity.Player;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MachPayIntegration {

    private final MachMC plugin;
    private final ConfigManager configManager;
    private final OkHttpClient httpClient;
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final String paykuEndpoint;

    public MachPayIntegration(MachMC plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.paykuEndpoint = configManager.getPaykuEndpoint();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String createPayment(Player player, String vipKey) {
        if (processingPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cYa estás procesando un pago. Por favor, espera.");
            return null;
        }

        processingPlayers.add(player.getUniqueId());

        int price = configManager.getVipPrice(vipKey);
        if (price <= 0) {
            player.sendMessage("§cEl precio del VIP no es válido.");
            processingPlayers.remove(player.getUniqueId());
            return null;
        }

        String orderId = "VIP-" + UUID.randomUUID().toString().substring(0, 8);
        JSONObject payload = buildPayload(player, orderId, price);

        plugin.getLogger().info("Enviando payload a Payku: " + payload.toJSONString());

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toJSONString());

        Request request = new Request.Builder()
                .url(paykuEndpoint)
                .post(body)
                .addHeader("Authorization", "Bearer " + configManager.getPaykuPublicKey())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String paymentUrl = handleResponse(response, player, orderId, vipKey);
            processingPlayers.remove(player.getUniqueId());
            return paymentUrl;
        } catch (IOException e) {
            plugin.getLogger().severe("Error al realizar la solicitud a Payku: " + e.getMessage());
            player.sendMessage("§cHubo un error al procesar tu pago. Inténtalo de nuevo más tarde.");
            processingPlayers.remove(player.getUniqueId());
            return null;
        }
    }

    private JSONObject buildPayload(Player player, String orderId, int price) {
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(30); // Expira en 30 minutos
        String formattedTime = expirationTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        JSONObject payload = new JSONObject();
        payload.put("amount", price);
        payload.put("expired", formattedTime); // Fecha de expiración futura
        payload.put("urlreturn", configManager.getSuccessUrl() + "?orderId=" + orderId);
        payload.put("urlnotify", configManager.getNotifyUrl() + "?orderId=" + orderId);
        payload.put("subject", "Compra de VIP para " + player.getName());
        payload.put("email", player.getName() + "@xgamers.cl");
        payload.put("order", orderId);
        payload.put("payment", configManager.getPaykuPaymentMethod()); // Método de pago desde config.yml
        return payload;
    }

    private String handleResponse(Response response, Player player, String orderId, String vipKey) throws IOException {
        if (response.body() == null) {
            plugin.getLogger().severe("Respuesta de Payku vacía");
            return null;
        }

        String responseBody = response.body().string();
        plugin.getLogger().info("Código de respuesta de Payku: " + response.code());
        plugin.getLogger().info("Cuerpo de respuesta de Payku: " + responseBody);

        if (response.code() == 401) {
            plugin.getLogger().severe("Error de autenticación con Payku - verificar claves API");
            player.sendMessage("§cError de configuración del sistema de pagos.");
            return null;
        }

        if (response.isSuccessful()) {
            try {
                JSONParser parser = new JSONParser();
                JSONObject responseJson = (JSONObject) parser.parse(responseBody);

                String status = (String) responseJson.get("status");
                if ("register".equalsIgnoreCase(status)) {
                    String paymentUrl = (String) responseJson.get("url");

                    // Guarda el pago pendiente
                    configManager.savePendingPayment(orderId, player.getUniqueId(), vipKey);

                    return paymentUrl;
                } else {
                    plugin.getLogger().warning("Estado no manejado: " + status);
                    player.sendMessage("§cError inesperado: estado del pago no reconocido.");
                }
            } catch (ParseException e) {
                plugin.getLogger().severe("Error al analizar la respuesta de Payku: " + e.getMessage());
            }
        } else {
            plugin.getLogger().warning("Error en la solicitud a Payku: " + responseBody);
            player.sendMessage("§cHubo un error al procesar tu compra.");
        }
        return null;
    }

    public void cleanup(Player player) {
        processingPlayers.remove(player.getUniqueId());
    }
}