package com.example.stickpowers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class StickPowers extends JavaPlugin implements Listener {
    
    private Map<UUID, PowerType> playerPowers = new HashMap<>();
    private List<PowerType> availablePowers = new ArrayList<>();
    private Random random = new Random();
    private int cooldownSeconds;
    private Map<UUID, Long> cooldowns = new HashMap<>();
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Load configuration
        loadConfiguration();
        
        getLogger().info("StickPowers plugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("StickPowers plugin has been disabled!");
    }
    
    private void loadConfiguration() {
        // Reload the config
        reloadConfig();
        FileConfiguration config = getConfig();
        
        // Get cooldown time from config
        cooldownSeconds = config.getInt("cooldown-seconds", 30);
        
        // Clear and reload available powers
        availablePowers.clear();
        
        // Add all powers from the config
        if (config.getConfigurationSection("powers") != null) {
            for (String key : config.getConfigurationSection("powers").getKeys(false)) {
                String path = "powers." + key;
                String displayName = config.getString(path + ".display-name", key);
                String description = config.getString(path + ".description", "");
                int duration = config.getInt(path + ".duration", 30);
                int amplifier = config.getInt(path + ".amplifier", 0);
                
                List<PotionEffectType> effects = new ArrayList<>();
                for (String effectName : config.getStringList(path + ".effects")) {
                    try {
                        PotionEffectType effect = PotionEffectType.getByName(effectName);
                        if (effect != null) {
                            effects.add(effect);
                        } else {
                            getLogger().warning("Invalid potion effect: " + effectName);
                        }
                    } catch (Exception e) {
                        getLogger().warning("Error loading effect: " + effectName);
                    }
                }
                
                PowerType powerType = new PowerType(key, displayName, description, effects, duration, amplifier);
                availablePowers.add(powerType);
            }
        }
        
        // If no powers are configured, add defaults
        if (availablePowers.isEmpty()) {
            createDefaultPowers();
        }
    }
    
    private void createDefaultPowers() {
        // Speed Power
        List<PotionEffectType> speedEffects = new ArrayList<>();
        speedEffects.add(PotionEffectType.SPEED);
        PowerType speedPower = new PowerType(
            "speed", 
            "Speed Boost", 
            "Run faster than the wind!", 
            speedEffects, 
            30, 
            1
        );
        availablePowers.add(speedPower);
        
        // Strength Power
        List<PotionEffectType> strengthEffects = new ArrayList<>();
        strengthEffects.add(PotionEffectType.INCREASE_DAMAGE);
        PowerType strengthPower = new PowerType(
            "strength", 
            "Super Strength", 
            "Become incredibly strong!", 
            strengthEffects, 
            30, 
            1
        );
        availablePowers.add(strengthPower);
        
        // Jump Power
        List<PotionEffectType> jumpEffects = new ArrayList<>();
        jumpEffects.add(PotionEffectType.JUMP);
        PowerType jumpPower = new PowerType(
            "jump", 
            "Super Jump", 
            "Leap tall buildings in a single bound!", 
            jumpEffects, 
            30, 
            2
        );
        availablePowers.add(jumpPower);
        
        // Resistance Power
        List<PotionEffectType> resistanceEffects = new ArrayList<>();
        resistanceEffects.add(PotionEffectType.DAMAGE_RESISTANCE);
        PowerType resistancePower = new PowerType(
            "resistance", 
            "Diamond Skin", 
            "Take less damage from attacks!", 
            resistanceEffects, 
            30, 
            1
        );
        availablePowers.add(resistancePower);
        
        // Night Vision Power
        List<PotionEffectType> nightVisionEffects = new ArrayList<>();
        nightVisionEffects.add(PotionEffectType.NIGHT_VISION);
        PowerType nightVisionPower = new PowerType(
            "night_vision", 
            "Night Vision", 
            "See in the dark!", 
            nightVisionEffects, 
            60, 
            0
        );
        availablePowers.add(nightVisionPower);
        
        // Save default powers to config
        FileConfiguration config = getConfig();
        for (PowerType power : availablePowers) {
            String path = "powers." + power.getId();
            config.set(path + ".display-name", power.getDisplayName());
            config.set(path + ".description", power.getDescription());
            config.set(path + ".duration", power.getDuration());
            config.set(path + ".amplifier", power.getAmplifier());
            
            List<String> effectNames = new ArrayList<>();
            for (PotionEffectType effect : power.getEffects()) {
                effectNames.add(effect.getName());
            }
            config.set(path + ".effects", effectNames);
        }
        
        config.set("cooldown-seconds", cooldownSeconds);
        saveConfig();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Assign a random power if player doesn't have one yet
        if (!playerPowers.containsKey(player.getUniqueId())) {
            assignRandomPower(player);
        }
        
        // Give player a power stick if they don't have one
        boolean hasStick = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isPowerStick(item)) {
                hasStick = true;
                break;
            }
        }
        
        if (!hasStick) {
            player.getInventory().addItem(createPowerStick(player));
        }
        
        // Inform player of their power
        PowerType power = playerPowers.get(player.getUniqueId());
        if (power != null) {
            player.sendMessage(ChatColor.GREEN + "Your assigned power is: " + 
                ChatColor.GOLD + power.getDisplayName() + 
                ChatColor.GREEN + " - " + power.getDescription());
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is using a power stick
        if (item != null && isPowerStick(item) && event.getAction().name().contains("RIGHT_CLICK")) {
            event.setCancelled(true);
            
            // Check for cooldown
            if (isOnCooldown(player)) {
                long timeLeft = (cooldowns.get(player.getUniqueId()) + (cooldownSeconds * 1000) - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "Power stick on cooldown! Please wait " + timeLeft + " seconds.");
                return;
            }
            
            // Activate power
            activatePower(player);
            
            // Set cooldown
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
    
    private boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        
        long lastUsed = cooldowns.get(player.getUniqueId());
        return (System.currentTimeMillis() - lastUsed) < (cooldownSeconds * 1000);
    }
    
    private void assignRandomPower(Player player) {
        if (availablePowers.isEmpty()) {
            return;
        }
        
        PowerType power = availablePowers.get(random.nextInt(availablePowers.size()));
        playerPowers.put(player.getUniqueId(), power);
    }
    
    private ItemStack createPowerStick(Player player) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        
        PowerType power = playerPowers.get(player.getUniqueId());
        if (power == null) {
            assignRandomPower(player);
            power = playerPowers.get(player.getUniqueId());
        }
        
        meta.setDisplayName(ChatColor.GOLD + "Power Stick: " + power.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + power.getDescription());
        lore.add(ChatColor.YELLOW + "Right-click to activate");
        lore.add(ChatColor.AQUA + "Cooldown: " + cooldownSeconds + " seconds");
        meta.setLore(lore);
        
        stick.setItemMeta(meta);
        return stick;
    }
    
    private boolean isPowerStick(ItemStack item) {
        if (item.getType() != Material.STICK) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        
        return meta.getDisplayName().contains("Power Stick");
    }
    
    private void activatePower(Player player) {
        PowerType power = playerPowers.get(player.getUniqueId());
        if (power == null) {
            player.sendMessage(ChatColor.RED + "You don't have a power assigned!");
            return;
        }
        
        // Apply potion effects
        for (PotionEffectType effectType : power.getEffects()) {
            player.addPotionEffect(new PotionEffect(
                effectType, 
                power.getDuration() * 20, // Convert seconds to ticks
                power.getAmplifier()
            ));
        }
        
        // Notify player
        player.sendMessage(ChatColor.GREEN + "You activated " + ChatColor.GOLD + power.getDisplayName() + ChatColor.GREEN + "!");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("stickpowers")) {
            if (!sender.hasPermission("stickpowers.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            
            if (args.length == 0) {
                sendHelpMessage(sender);
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                loadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "StickPowers configuration reloaded!");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("info")) {
                sender.sendMessage(ChatColor.GOLD + "=== StickPowers Info ===");
                sender.sendMessage(ChatColor.YELLOW + "Available powers: " + availablePowers.size());
                sender.sendMessage(ChatColor.YELLOW + "Cooldown: " + cooldownSeconds + " seconds");
                
                if (sender instanceof Player && args.length > 1 && args[1].equalsIgnoreCase("powers")) {
                    sender.sendMessage(ChatColor.GOLD + "Available Powers:");
                    for (PowerType power : availablePowers) {
                        sender.sendMessage(ChatColor.YELLOW + "- " + power.getDisplayName() + ": " + power.getDescription());
                    }
                }
                
                return true;
            }
            
            if (args[0].equalsIgnoreCase("give") && sender instanceof Player && args.length > 1) {
                Player targetPlayer = getServer().getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                
                targetPlayer.getInventory().addItem(createPowerStick(targetPlayer));
                sender.sendMessage(ChatColor.GREEN + "Power stick given to " + targetPlayer.getName());
                return true;
            }
            
            if (args[0].equalsIgnoreCase("assign") && args.length > 1) {
                Player targetPlayer = getServer().getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }
                
                assignRandomPower(targetPlayer);
                PowerType power = playerPowers.get(targetPlayer.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Assigned " + power.getDisplayName() + " to " + targetPlayer.getName());
                targetPlayer.sendMessage(ChatColor.GREEN + "You were assigned a new power: " + 
                    ChatColor.GOLD + power.getDisplayName() + 
                    ChatColor.GREEN + " - " + power.getDescription());
                return true;
            }
            
            sendHelpMessage(sender);
            return true;
        }
        
        return false;
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== StickPowers Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/stickpowers reload " + ChatColor.WHITE + "- Reload the config");
        sender.sendMessage(ChatColor.YELLOW + "/stickpowers info [powers] " + ChatColor.WHITE + "- Show plugin info");
        sender.sendMessage(ChatColor.YELLOW + "/stickpowers give <player> " + ChatColor.WHITE + "- Give a power stick");
        sender.sendMessage(ChatColor.YELLOW + "/stickpowers assign <player> " + ChatColor.WHITE + "- Assign a new random power");
    }
    
    // Inner class to represent a power type
    private class PowerType {
        private final String id;
        private final String displayName;
        private final String description;
        private final List<PotionEffectType> effects;
        private final int duration;
        private final int amplifier;
        
        public PowerType(String id, String displayName, String description, List<PotionEffectType> effects, int duration, int amplifier) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.effects = effects;
            this.duration = duration;
            this.amplifier = amplifier;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public String getDescription() {
            return description;
        }
        
        public List<PotionEffectType> getEffects() {
            return effects;
        }
        
        public int getDuration() {
            return duration;
        }
        
        public int getAmplifier() {
            return amplifier;
        }
    }
}
