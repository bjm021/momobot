package de.bjm.momobot;

import de.bjm.momobot.file.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

public class Bootstrap {

    private static JDA jda;

    public static JDA getJda() {
        return jda;
    }

    private static Config config;

    public static Config getConfig() {
        return config;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Starting BOT!");
        config = new Config();

        String token = config.getValue(Config.ConfigValue.TOKEN);
        if ("none".equalsIgnoreCase(token)) {
            System.err.println("Please update your config.properties file with a bot token!");
            System.exit(1);
        }

        jda = new JDABuilder()
                .setToken(token)
                .addEventListeners(new BotApplicationManager())
                .setActivity(Activity.playing("-help | momobot.cf"))
                .build();
    }
}
