package cl.xgamers.machMC.webhook;

import cl.xgamers.machMC.MachMC;
import cl.xgamers.machMC.config.ConfigManager;
import spark.Spark;

public class WebhookServer {

    private final MachMC plugin;
    private final ConfigManager configManager;
    private final PaykuWebhookHandler webhookHandler;

    public WebhookServer(MachMC plugin, PaykuWebhookHandler webhookHandler) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.webhookHandler = webhookHandler;
        setupWebhookRoutes();
    }

    private void setupWebhookRoutes() {
        int port = configManager.getWebhookPort();
        String secret = configManager.getWebhookSecret();

        Spark.port(port);

        Spark.post("/notify", (request, response) -> {
            String authHeader = request.headers("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer " + secret)) {
                response.status(401);
                return "Unauthorized";
            }

            try {
                String payload = request.body();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        webhookHandler.handleWebhook(payload));
                response.status(200);
                return "OK";
            } catch (Exception e) {
                plugin.getLogger().severe("Error procesando el webhook: " + e.getMessage());
                e.printStackTrace();
                response.status(500);
                return "Error";
            }
        });

        Spark.get("/success", (request, response) -> {
            response.type("text/html");
            return "<html><head><title>Pago Exitoso</title></head><body style='font-family: Arial, sans-serif; text-align: center;'><h1>¡Pago exitoso!</h1><p>Gracias por tu compra. Vuelve al juego para recibir tu VIP.</p></body></html>";
        });

        Spark.get("/cancel", (request, response) -> {
            response.type("text/html");
            return "<html><head><title>Pago Cancelado</title></head><body style='font-family: Arial, sans-serif; text-align: center;'><h1>Pago cancelado</h1><p>No se ha realizado el pago. Intenta nuevamente si deseas completar tu compra.</p></body></html>";
        });

        Spark.awaitInitialization();
        plugin.getLogger().info("§aServidor de Webhooks iniciado en el puerto " + port);
    }

    public void shutdown() {
        Spark.stop();
        Spark.awaitStop();
    }
}