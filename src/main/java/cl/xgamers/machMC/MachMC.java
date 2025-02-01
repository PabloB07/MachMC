package cl.xgamers.machMC;

import cl.xgamers.machMC.commands.MachMCCommand;
import cl.xgamers.machMC.config.ConfigManager;
import cl.xgamers.machMC.gui.VIPGUI;
import cl.xgamers.machMC.payments.MachPayIntegration;
import cl.xgamers.machMC.webhook.PaykuWebhookHandler;
import cl.xgamers.machMC.webhook.WebhookServer;
import lombok.Getter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class MachMC extends JavaPlugin implements Listener {

    @Getter
    private ConfigManager configManager;
    private WebhookServer webhookServer;
    private PaykuWebhookHandler webhookHandler;
    private LuckPerms luckPerms;
    private VIPGUI vipGUI;
    private MachPayIntegration machPayIntegration;
    @Getter
    private static MachMC instance;

    @Override
    public void onEnable() {
        instance = this;

        this.configManager = new ConfigManager(this);
        this.machPayIntegration = new MachPayIntegration(this, configManager);
        this.vipGUI = new VIPGUI(this, machPayIntegration, configManager);

        // Setup LuckPerms
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        } else {
            getLogger().severe("LuckPerms no encontrado! El plugin no funcionará correctamente.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        // Register commands
        getCommand("machmc").setExecutor(new MachMCCommand(this, configManager, vipGUI));

        // Setup webhook server
        this.webhookHandler = new PaykuWebhookHandler(this, luckPerms);
        this.webhookServer = new WebhookServer(this, webhookHandler);

        getLogger().info("MachMC ha sido habilitado correctamente!");
        checkPlaceholderAPI();
    }

    @Override
    public void onDisable() {
        getLogger().info("MachMC ha sido deshabilitado.");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String guiTitle = LegacyComponentSerializer.legacySection().serialize(configManager.getGuiTitle());
        if (event.getView().getTitle().equals(guiTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                Player player = (Player) event.getWhoClicked();
                vipGUI.handleVIPItemClick(player, event.getCurrentItem());
            }
        }
    }

    private void checkPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("No se encuentra PlaceHolderAPI en tu carpeta plugins, habilitalo!");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
}