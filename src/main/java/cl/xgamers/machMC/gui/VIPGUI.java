package cl.xgamers.machMC.gui;

import cl.xgamers.machMC.MachMC;
import cl.xgamers.machMC.config.ConfigManager;
import cl.xgamers.machMC.payments.MachPayIntegration;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class VIPGUI implements Listener {
    private final MachMC plugin;
    private final MachPayIntegration machPayIntegration;
    private final ConfigManager configManager;
    @Getter
    private Inventory inventory;
    private final Set<UUID> processingPlayers = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final long COOLDOWN_TIME = 3000;

    public VIPGUI(MachMC plugin, MachPayIntegration machPayIntegration, ConfigManager configManager) {
        this.plugin = plugin;
        this.machPayIntegration = machPayIntegration;
        this.configManager = configManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        buildGUI();
    }

    public void openGUI(Player player) {
        if (inventory == null) {
            buildGUI();
            if (inventory == null) {
                player.sendMessage(miniMessage.deserialize("<red>Error al abrir la GUI. Intenta nuevamente.</red>"));
                return;
            }
        }

        if (processingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<red>Ya estás procesando un pago. Por favor, espera.</red>"));
            return;
        }

        player.openInventory(inventory);
    }

    public void buildGUI() {
        int size = configManager.getGuiSize();
        String title = LegacyComponentSerializer.legacySection().serialize(configManager.getGuiTitle());
        inventory = Bukkit.createInventory(null, size, title);

        ConfigurationSection vipsSection = plugin.getConfig().getConfigurationSection("vips");
        if (vipsSection == null) return;

        vipsSection.getKeys(false).forEach(vipKey -> {
            ConfigurationSection vipSection = vipsSection.getConfigurationSection(vipKey);
            if (vipSection == null) return;

            ItemStack item = createVIPItem(vipSection, vipKey);
            inventory.addItem(item);
        });
    }

    private ItemStack createVIPItem(ConfigurationSection vipSection, String vipKey) {
        String displayName = vipSection.getString("display-name", vipKey);
        List<String> lore = vipSection.getStringList("lore");
        String materialName = vipSection.getString("material", "DIAMOND");
        double price = vipSection.getDouble("price", 0);

        Material material = Material.valueOf(materialName.toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(miniMessage.deserialize(displayName));
            meta.lore(colorLore(lore, price));
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String guiTitle = LegacyComponentSerializer.legacySection().serialize(configManager.getGuiTitle());
        if (!event.getView().getTitle().equals(guiTitle)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        Player player = (Player) event.getWhoClicked();

        long currentTime = System.currentTimeMillis();
        if (cooldowns.containsKey(player.getUniqueId())) {
            long lastClickTime = cooldowns.get(player.getUniqueId());
            if (currentTime - lastClickTime < COOLDOWN_TIME) {
                player.sendMessage(miniMessage.deserialize("<red>Espera un momento antes de hacer otro clic.</red>"));
                return;
            }
        }

        cooldowns.put(player.getUniqueId(), currentTime);

        if (processingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(miniMessage.deserialize("<red>Ya estás procesando un pago. Por favor, espera.</red>"));
            return;
        }

        handleVIPItemClick(player, clickedItem);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        machPayIntegration.cleanup(player);
        processingPlayers.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }

    public void handleVIPItemClick(Player player, ItemStack clickedItem) {
        ConfigurationSection vipsSection = plugin.getConfig().getConfigurationSection("vips");
        if (vipsSection == null) return;

        for (String vipKey : vipsSection.getKeys(false)) {
            ConfigurationSection vipSection = vipsSection.getConfigurationSection(vipKey);
            if (vipSection == null) continue;

            String configDisplayName = vipSection.getString("display-name", "");

            if (clickedItem.getItemMeta().displayName().equals(miniMessage.deserialize(configDisplayName))) {
                if (processingPlayers.contains(player.getUniqueId())) {
                    player.sendMessage(miniMessage.deserialize("<red>Ya estás procesando un pago. Por favor, espera.</red>"));
                    return;
                }

                processingPlayers.add(player.getUniqueId());
                processVIPPurchase(player, vipKey);
                break;
            }
        }
    }

    private void processVIPPurchase(Player player, String vipKey) {
        UUID playerId = player.getUniqueId();

        new BukkitRunnable() {
            @Override
            public void run() {
                String paymentUrl = machPayIntegration.createPayment(player, vipKey);
                if (paymentUrl != null) {
                    // Cerrar el inventario en el hilo principal
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.closeInventory();
                    });
                    sendPaymentMessage(player, paymentUrl);
                } else {
                    player.sendMessage(miniMessage.deserialize("<red>Error al generar el pago. Intenta nuevamente.</red>"));
                }

                // Quitar al jugador del proceso
                processingPlayers.remove(playerId);
            }
        }.runTaskAsynchronously(plugin); // Ejecutar en un hilo asíncrono
    }

    private List<Component> colorLore(List<String> lore, double price) {
        List<Component> coloredLore = new ArrayList<>();
        if (lore != null) {
            for (String line : lore) {
                // Reemplaza los símbolos especiales con su representación en MiniMessage
                line = line.replace("✔", "<green>✔</green>"); // Asegurar de que el símbolo ✔ esté formateado correctamente
                coloredLore.add(miniMessage.deserialize(line));
            }
        }
        // Agrega el precio al lore
        coloredLore.add(miniMessage.deserialize("<gray>Precio: $" + String.format("%,.0f", price) + "</gray>"));
        return coloredLore;
    }

    private void sendPaymentMessage(Player player, String paymentUrl) {
        Component message = miniMessage.deserialize("<green>→ Click aquí para pagar ←</green>")
                .clickEvent(ClickEvent.openUrl(paymentUrl));
        player.sendMessage(message);
        player.sendMessage(miniMessage.deserialize("<green>Tu enlace de pago: revisa el chat para pagarlo.</green>"));
        player.sendMessage(miniMessage.deserialize("<yellow>¡El enlace de pago es temporal! Asegúrate de completar el pago pronto.</yellow>"));
    }

    public void reloadGUI() {
        try {
            inventory.clear();
            buildGUI();
            plugin.getLogger().info("GUI VIP recargada correctamente");
        } catch (Exception e) {
            plugin.getLogger().severe("Error al recargar la GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}