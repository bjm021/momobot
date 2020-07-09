package de.bjm.momobot;

import de.bjm.momobot.file.Config;
import de.bjm.momobot.file.QueueFile;
import de.bjm.momobot.utils.Setup;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.managers.AccountManager;
import net.dv8tion.jda.api.managers.Presence;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

/**
 * momobot - discord music bot with live eq based on lavaplayer
 * Copyright (C) 2019-2020 Benjamin J. Meyer
 *
 * This is a simple Discord music bot created just for fun.
 * It is based on lavaplayer and uses JDA to connect to Discord
 *
 * This class starts the bot and initializes everything it needs
 */
public class Bootstrap {

    /**
     * The Version Identifier
     */
    public static final String VERSION = "v1.7";

    /**
     * The active JDA connection to discord
     */
    private static JDA jda;

    /**
     * Access to the running JDA instance from everywhere
     * @return  The running {@link JDA} connection
     */
    public static JDA getJda() {
        return jda;
    }

    /**
     * An instance of the config class used for config adjustments on the fly
     */
    private static Config config;

    /**
     * An instance similar to the config class however this handles the save / load of queses to / from a file
     */
    private static QueueFile queueFile;

    /**
     * Allow other classes to access the config
     * @return  The {@link Config} Class
     */
    public static Config getConfig() {
        return config;
    }

    /**
     * Allow other classes to access the queueFile
     * @return  The {@link QueueFile} class
     */
    public static QueueFile getQueueFile() {
        return queueFile;
    }

    /**
     * Java main method
     * @param args          The args of the user
     * @throws Exception    Exception thrown if the bot builder encounters an error
     */
    public static void main(String[] args) throws Exception {

        System.out.println("Starting BOT!");

        // test config
        if (!new File("." + File.separator + "config.json").exists()) {
            Scanner in = new Scanner(System.in);
            System.out.println("[momobot] It seems this is the first time you run this bot!");
            System.out.println("[momobot] Do you want to run the setup? (y/n)");
            System.out.print("> ");
            String entry = in.next();
            if (entry.equalsIgnoreCase("y")) {
                new Setup();
            } else {
                System.out.println("[momobot] Ok then I will star the bot now!");
            }
        }

        config = new Config();
        queueFile = new QueueFile();

        String token = config.getValue(Config.ConfigValue.TOKEN);


        JDABuilder builder = JDABuilder.createDefault(token);
        builder.addEventListeners(new BotApplicationManager());
        builder.setActivity(Activity.playing(config.getValue(Config.ConfigValue.PREFIX) + "help | momobot.cf"));
        jda = builder.build();

        System.setProperty("http.agent", "");

    }
}
