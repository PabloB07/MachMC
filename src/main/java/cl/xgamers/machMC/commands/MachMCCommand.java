package cl.xgamers.machMC.commands;

import cl.xgamers.machMC.MachMC;
import cl.xgamers.machMC.config.ConfigManager;
import cl.xgamers.machMC.gui.VIPGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MachMCCommand implements CommandExecutor {

    private final MachMC plugin;
    private final ConfigManager configManager;
    private final VIPGUI vipGUI;

    public MachMCCommand(MachMC plugin, ConfigManager configManager, VIPGUI vipGUI) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.vipGUI = vipGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§cUsa: /machmc <help|reload|vip>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                return handleHelpCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "vip":
                return handleVIPCommand(sender);
            default:
                sender.sendMessage("§cComando no reconocido. Usa /machmc help para ver los comandos disponibles.");
                return true;
        }
    }

    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage("§a=== Comandos de MachMC ===");
        sender.sendMessage("§a/machmc help §7- Muestra esta ayuda.");
        sender.sendMessage("§a/machmc reload §7- Recarga la configuración del plugin.");
        sender.sendMessage("§a/machmc vip §7- Abre el menú de compra de VIP.");
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("machmc.reload")) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        configManager.reloadConfig();
        configManager.loadConfig();
        vipGUI.reloadGUI();
        sender.sendMessage("§aConfiguración de MachMC recargada correctamente.");
        return true;
    }

    private boolean handleVIPCommand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        Player player = (Player) sender;
        vipGUI.openGUI(player);
        return true;
    }
}