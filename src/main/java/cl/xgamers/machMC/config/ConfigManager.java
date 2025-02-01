package cl.xgamers.machMC.config;

import cl.xgamers.machMC.MachMC;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ConfigManager {

    private final MachMC plugin;
    @Getter
    private FileConfiguration config;
    private File configFile;
    private final Map<UUID, Map<String, Object>> pendingVIPs;

    // Instancia de MiniMessage para procesar mensajes
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ConfigManager(MachMC plugin) {
        this.plugin = plugin;
        this.pendingVIPs = new HashMap<>();
        loadConfig();
    }

    /**
     * Carga el archivo config.yml y los VIPs pendientes.
     */
    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        reloadPendingVIPs();
        validateConfiguration();
        convertLegacyMessages();
    }

    private void validateConfiguration() {
        if (getPaykuPublicKey().isEmpty()) {
            plugin.getLogger().severe("¡Las claves de la API de Payku no están configuradas!");
        }
        if (getWebhookSecret().equals("default_secret")) {
            plugin.getLogger().warning("Estás usando un webhook secret predeterminado. ¡Por favor configúralo!");
        }
        if (getNotifyUrl().contains("mi-dominio.com")) {
            plugin.getLogger().warning("¡Se detectó la URL de notificación predeterminada! Por favor configúrala correctamente.");
        }
    }

    private void convertLegacyMessages() {
        boolean needsSave = false;
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");

        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(true)) {
                String value = messagesSection.getString(key);
                if (value != null && value.contains("§")) {
                    messagesSection.set(key, legacyToMiniMessage(value));
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            saveConfig();
            plugin.getLogger().info("Se han convertido mensajes legacy a formato MiniMessage.");
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar el archivo config.yml: " + e.getMessage());
            createBackupConfig();
        }
    }

    private void createBackupConfig() {
        File backupFile = new File(plugin.getDataFolder(), "config-backup.yml");
        try {
            config.save(backupFile);
            plugin.getLogger().warning("Se creó una copia de seguridad de config.yml: " + backupFile.getPath());
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo crear la copia de seguridad de config.yml: " + e.getMessage());
        }
    }

    private void reloadPendingVIPs() {
        pendingVIPs.clear();
        ConfigurationSection section = config.getConfigurationSection("pending-vips");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                String rank = section.getString(key + ".rank");
                long expiration = section.getLong(key + ".expiration");
                Map<String, Object> vipData = new HashMap<>();
                vipData.put("rank", rank);
                vipData.put("expiration", expiration);
                pendingVIPs.put(uuid, vipData);
            }
        }
    }

    public void savePendingVIP(UUID uuid, String rank, long expiration) {
        if (uuid == null || rank == null) {
            plugin.getLogger().warning("Intento de guardar VIP con datos inválidos.");
            return;
        }

        Map<String, Object> vipData = new HashMap<>();
        vipData.put("rank", rank);
        vipData.put("expiration", expiration);
        pendingVIPs.put(uuid, vipData);

        config.set("pending-vips." + uuid + ".rank", rank);
        config.set("pending-vips." + uuid + ".expiration", expiration);
        saveConfig();

        plugin.getLogger().info("VIP pendiente guardado: " + uuid + " - " + rank);
    }

    public Component renderMessage(String path, Player player, Map<String, String> placeholders) {
        String rawMessage = config.getString(path);
        if (rawMessage == null || rawMessage.isEmpty()) {
            return Component.empty();
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") && player != null) {
            rawMessage = PlaceholderAPI.setPlaceholders(player, rawMessage);
        }

        List<TagResolver> resolvers = new ArrayList<>();
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                resolvers.add(Placeholder.parsed(entry.getKey(), entry.getValue()));
            }
        }

        return miniMessage.deserialize(rawMessage, TagResolver.resolver(resolvers));
    }

    public void sendMessage(Player player, String path, Map<String, String> placeholders) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Component message = renderMessage(path, player, placeholders);
        if (!message.equals(Component.empty())) {
            player.sendMessage(message);
        }
    }

    public String legacyToMiniMessage(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        return legacy
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");
    }

    // Getters
    public String getPaykuEndpoint() {
        // Si el endpoint está configurado manualmente, úsalo
        String customEndpoint = config.getString("payku.endpoint");
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            return customEndpoint;
        }
        String mode = config.getString("payku.mode", "production").toLowerCase();
        return mode.equals("development") ?
                "https://des.payku.cl/api/transaction" : // Endpoint de desarrollo
                "https://app.payku.cl/api/transaction";  // Endpoint de producción
    }
    public int getPaykuPaymentMethod() {
        return config.getInt("payku.payment-method", 1); // Valor por defecto: 1 (WebPay)
    }

    public String getPaykuPublicKey() {
        String mode = config.getString("payku.mode", "production").toLowerCase();
        return mode.equals("development") ?
                config.getString("payku.dev-public-key", "") : // Clave pública de desarrollo
                config.getString("payku.prod-public-key", ""); // Clave pública de producción
    }

    public String getWebhookSecret() {
        return config.getString("webhook.secret", "default_secret");
    }

    public String getNotifyUrl() {
        return config.getString("urls.notify", "https://mi-dominio.com/notify");
    }

    public Component getGuiTitle() {
        String rawTitle = config.getString("gui.title", "<green>Tienda VIP</green>");
        return miniMessage.deserialize(rawTitle);
    }

    public int getGuiSize() {
        return config.getInt("gui.size", 27);
    }

    public int getVipPrice(String vipKey) {
        return config.getInt("vips." + vipKey + ".price", 0);
    }

    public Component getVipDisplayName(String vipKey) {
        String rawName = config.getString("vips." + vipKey + ".display-name", vipKey);
        return miniMessage.deserialize(rawName);
    }

    public String getVipRank(String vipKey) {
        return config.getString("vips." + vipKey + ".rank", "");
    }

    public String getSuccessUrl() {
        return config.getString("urls.success", "");
    }

    public int getWebhookPort() {
        return config.getInt("webhook.port", 8080);
    }

    public void savePendingPayment(String orderId, UUID playerUUID, String vipKey) {
        if (orderId == null || playerUUID == null || vipKey == null) {
            plugin.getLogger().warning("Intento de guardar un pago pendiente con datos inválidos.");
            return;
        }

        String path = "pending-payments." + orderId;
        config.set(path + ".player", playerUUID.toString());
        config.set(path + ".vip-rank", vipKey);
        config.set(path + ".timestamp", System.currentTimeMillis());

        saveConfig();
        plugin.getLogger().info("Pago pendiente guardado: Order ID = " + orderId + ", Player UUID = " + playerUUID + ", VIP Key = " + vipKey);
    }

    public void removePendingPayment(String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            plugin.getLogger().warning("Intento de eliminar un pago pendiente con un ID inválido.");
            return;
        }

        config.set("pending-payments." + orderId, null);
        saveConfig();

        plugin.getLogger().info("Pago pendiente eliminado: " + orderId);
    }

    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Configuración recargada correctamente.");
    }
}