package org.by1337.bmenu.factory;

import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.by1337.blib.chat.placeholder.Placeholder;
import org.by1337.blib.configuration.YamlContext;
import org.by1337.blib.configuration.YamlValue;
import org.by1337.blib.util.Pair;
import org.by1337.bmenu.MenuItemBuilder;
import org.by1337.bmenu.MenuLoader;
import org.by1337.bmenu.click.ClickHandlerImpl;
import org.by1337.bmenu.click.MenuClickType;
import org.by1337.bmenu.requirement.Requirements;
import org.by1337.bmenu.util.ObjectUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class ItemFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger("BMenu#ItemFactory");

    public static Map<String, MenuItemBuilder> readItems(Map<String, YamlContext> items, MenuLoader loader) {
        Map<String, MenuItemBuilder> itemMap = new HashMap<>();

        for (String itemID : items.keySet()) {
            YamlContext ctx = items.get(itemID);
            itemMap.put(itemID, readItem(ctx, loader));
        }
        return itemMap;
    }

    public static MenuItemBuilder readItem(YamlContext ctx, MenuLoader loader){
        MenuItemBuilder builder = new MenuItemBuilder();
        Placeholder argsReplacer = new Placeholder();
        Map<String, String> args = ctx.get("args").getAsMap(String.class, Collections.emptyMap());
        builder.setArgs(args);
//            args.forEach((key, value) -> // NOP
//                    argsReplacer.registerPlaceholder("${" + key + "}", () -> value)
//            );
        builder.setMaterial(argsReplacer.replace(ctx.get("material").getAsString("STONE")));
        builder.setName(ObjectUtil.mapIfNotNull(ctx.get("display_name").getAsString(null), argsReplacer::replace));
        builder.setAmount(argsReplacer.replace(ctx.get("amount").getAsString("1")));
        builder.setLore(ctx.get("lore").getAsList(YamlValue::getAsString, Collections.emptyList()).stream().map(argsReplacer::replace).toList());
        builder.setItemFlags(ctx.get("item_flags").getAsList(YamlValue::getAsString, Collections.emptyList()).stream().map(s -> ItemFlag.valueOf(argsReplacer.replace(s))).toList());
        builder.setPotionEffects(
                ctx.get("potion_effects").getAsList(YamlValue::getAsString, Collections.emptyList()).stream().map(argsReplacer::replace).toList()
                        .stream().map(s -> {
                            String[] args0 = argsReplacer.replace(s).split(";");
                            if (args0.length != 3) {
                                LOGGER.error("expected <PotionEffectType>;<duration>;<amplifier>, not {}", argsReplacer.replace(s));
                                return null;
                            }
                            PotionEffectType type = Objects.requireNonNull(PotionEffectType.getByName(args0[0].toLowerCase(Locale.ENGLISH)), "PotionEffectType is null");
                            int duration = Integer.parseInt(args0[1]);
                            int amplifier = Integer.parseInt(args0[2]);
                            return new PotionEffect(type, duration, amplifier);
                        }).filter(Objects::nonNull).toList()

        );
        builder.setColor(ctx.getAs("color", Color.class, null));
        builder.setEnchantments(
                ctx.get("enchantments").getAsList(YamlValue::getAsString, Collections.emptyList()).stream().map(argsReplacer::replace).toList()
                        .stream().map(s -> {
                            String[] args0 = argsReplacer.replace(s).split(";");
                            if (args0.length != 2) {
                                LOGGER.error("was expected to be enchantmentid;level, not {}", argsReplacer.replace(s));
                                return null;
                            }
                            Enchantment type = Objects.requireNonNull(Enchantment.getByKey(NamespacedKey.minecraft(args0[0].toLowerCase(Locale.ENGLISH))), "Enchantment is null");
                            int level = Integer.parseInt(args0[1]);
                            return Pair.of(type, level);
                        }).filter(Objects::nonNull).toList()
        );
        builder.setUnbreakable(ctx.getAsBoolean("unbreakable", false));
        builder.setModelData(ctx.getAsInteger("model_data", 0));
        builder.setPriority(ctx.getAsInteger("priority", 0));
        builder.setSlots(getSlots(ctx));
        builder.setViewRequirement(
                ObjectUtil.mapIfNotNullOrDefault(
                        ctx.get("view_requirement.requirements"),
                        v -> RequirementsFactory.read(v, loader, argsReplacer),
                        Requirements.EMPTY
                ),
                ctx.getList("view_requirement.deny_commands", String.class, Collections.emptyList())
        );

        for (MenuClickType value : MenuClickType.values()) {
            String key = value.getConfigKeyClick();
            if (ctx.getHandle().contains(key)) {
                builder.addClickListener(
                        value,
                        new ClickHandlerImpl(
                                ctx.get(key + ".deny_commands")
                                        .getAsList(YamlValue::getAsString, Collections.emptyList())
                                        .stream().map(argsReplacer::replace).toList()
                                ,
                                ctx.get(key + ".commands").getAsList(YamlValue::getAsString, Collections.emptyList())
                                        .stream().map(argsReplacer::replace).toList()
                                ,
                                ObjectUtil.mapIfNotNullOrDefault(
                                        ctx.get(key + ".requirements"),
                                        v -> RequirementsFactory.read(v, loader, argsReplacer),
                                        Requirements.EMPTY
                                )
                        )
                );
            }
        }
        builder.setTicking(ctx.getAsBoolean("ticking", false));
        builder.setTickSpeed(ctx.getAsInteger("tick-speed", 1));
        builder.setDamage(ctx.getAsInteger("damage", 1));
        return builder;
    }


    public static int[] getSlots(YamlContext context) {
        List<Integer> slots = new ArrayList<>();
        try {
            if (context.getHandle().contains("slot")) {
                slots.add(context.getAsInteger("slot"));
            }
            if (context.getHandle().contains("slots")) {
                for (String str : context.getList("slots", String.class)) {
                    if (str.contains("-")) {
                        String[] s = str.replace(" ", "").split("-");
                        int x = Integer.parseInt(s[0]);
                        int x1 = Integer.parseInt(s[1]);
                        for (int i = Math.min(x, x1); i <= Math.max(x, x1); i++) {
                            slots.add(i);
                        }
                    } else {
                        int x = Integer.parseInt(str.replace(" ", ""));
                        slots.add(x);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse slots", e);
            return new int[]{-1};
        }
        if (slots.isEmpty()) {
            return new int[]{-1};
        }
        int[] slot = new int[slots.size()];
        for (int x = 0; x < slots.size(); x++) {
            slot[x] = slots.get(x);
        }
        return slot;
    }
}
