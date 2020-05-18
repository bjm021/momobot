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
        if (Bootstrap.getConfig().getValue(Config.ConfigValue.DEBUG).equalsIgnoreCase("true") && e != null) {
            eb.setTitle("Deug Message: " + err);
        }
        eb.setDescription(err);
        eb.setColor(Color.RED);
        if (Bootstrap.getConfig().getValue(Config.ConfigValue.DEBUG).equalsIgnoreCase("true") && e != null) {
            if (Arrays.toString(e.getStackTrace()).length() >= 2040) {
                eb.setDescription(Arrays.toString(e.getStackTrace()).substring(0, 2040) + " ...");
            } else {
                eb.setDescription(Arrays.toString(e.getStackTrace()) + " ...");
            }
        }
        if (e != null)
            e.printStackTrace();
        return eb.build();
    }

    public static MessageEmbed buildSuccess(String msg) {
        eb = new EmbedBuilder();
        eb.setDescription(msg);
        eb.setColor(Color.GREEN);
        return eb.build();
    }

}
