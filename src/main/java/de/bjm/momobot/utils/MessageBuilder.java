package de.bjm.momobot.utils;

import de.bjm.momobot.Bootstrap;
import de.bjm.momobot.file.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.*;
import java.util.Arrays;

public class MessageBuilder {

    private static EmbedBuilder eb;

    public static MessageEmbed buildError(String err, Exception e) {
        eb = new EmbedBuilder();
        if (Bootstrap.getConfig().getValue(Config.ConfigValue.DEBUG).equalsIgnoreCase("true")) {
            eb.setTitle("Deug Message: " + err);
        }
        eb.setDescription(err);
        eb.setColor(Color.RED);
        if (Bootstrap.getConfig().getValue(Config.ConfigValue.DEBUG).equalsIgnoreCase("true")) {
            eb.setDescription(Arrays.toString(e.getStackTrace()).substring(0, 2043) + " ...");
        }
        return eb.build();
    }

    public static MessageEmbed buildSuccess(String msg) {
        eb = new EmbedBuilder();
        eb.setDescription(msg);
        eb.setColor(Color.GREEN);
        return eb.build();
    }

}
