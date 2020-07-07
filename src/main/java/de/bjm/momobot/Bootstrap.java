package de.bjm.momobot;

import de.bjm.momobot.file.Config;
import de.bjm.momobot.file.QueueFile;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

public class Bootstrap {

    public static final String VERSION = "v1.5";

    private static JDA jda;

    public static JDA getJda() {
        return jda;
    }

    private static Config config;

    private static QueueFile queueFile;

    public static Config getConfig() {
        return config;
    }

    public static QueueFile getQueueFile() {
        return queueFile;
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Starting BOT!");
        config = new Config();
        queueFile = new QueueFile();

        String token = config.getValue(Config.ConfigValue.TOKEN);
        if ("TOKEN-HERE".equalsIgnoreCase(token)) {
            System.err.println("Please update your config.properties file with a bot token!");
            System.exit(1);
        }


        JDABuilder builder = JDABuilder.createDefault(token);
        builder.addEventListeners(new BotApplicationManager());
        builder.setActivity(Activity.playing("-help | momobot.cf"));
        jda = builder.build();
    }
}
