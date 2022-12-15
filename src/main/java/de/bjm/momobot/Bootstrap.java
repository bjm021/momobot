package de.bjm.momobot;

import de.bjm.momobot.file.Config;
import de.bjm.momobot.file.QueueFile;
import de.bjm.momobot.utils.Setup;
import de.bjm.momobot.utils.TWOLogic;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.File;
import java.util.Scanner;

/**
 * momobot - discord music bot with live eq based on lavaplayer
 * Copyright (C) 2019-2020 Benjamin J. Meyer
 *
 * This is a simple Discord music bot created just for fun.
 * It is based on lavaplayer and uses JDA to connect to Discord
 *
 * This class starts the bot and initializes everything it needs
 *
 *  Copyright (C) 2020-2021 BJM SoftwareWorks (Benjamin J. Meyer)
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */
public class Bootstrap {

    /**
     * The Version Identifier
     */
    public static final String VERSION = "v2.2";

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

    private static TWOLogic twoLogic;

    public static TWOLogic getTwoLogic() {
        return twoLogic;
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
        twoLogic = new TWOLogic();
        queueFile = new QueueFile();

        String token = config.getValue(Config.ConfigValue.TOKEN);


        JDABuilder builder = JDABuilder.createDefault(token);
        builder.addEventListeners(new BotApplicationManager());
        //builder.setActivity(Activity.playing(config.getValue(Config.ConfigValue.PREFIX) + "help | momobot.cf"));
        builder.setActivity(Activity.playing(config.getValue(Config.ConfigValue.PREFIX) + "help | momobot.cf"));
        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_VOICE_STATES);
        jda = builder.build();

        System.setProperty("http.agent", "");

    }
}
