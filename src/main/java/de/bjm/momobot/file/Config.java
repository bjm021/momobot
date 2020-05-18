package de.bjm.momobot.file;

import de.bjm.momobot.Bootstrap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;

import java.io.*;
import java.util.Properties;

public class Config {

    public enum ConfigValue {
        DEBUG, TOKEN
    }

    File configFile = new File("." + File.separator + "config.properties");

    Properties config = new Properties();
    public File getConfigFile() {
        return configFile;
    }

    public Config() {
        if (!configFile.exists()) {
            try {
                // load standard values
                config.setProperty("DEBUG", "true");
                config.setProperty("TOKEN", "none");

                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                updateFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateFile() {
        try {
            OutputStream stream = new FileOutputStream(configFile);
            config.store(stream, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readFile() {
        try {
            InputStream is = new FileInputStream(configFile);
            config.clear();
            config.load(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getValue(ConfigValue string) {
        readFile();
        return config.getProperty(string.toString());
    }

    public VoiceChannel getVoiceChannelToUse(Guild guild) {
        readFile();
        System.out.println("[DEBUG] getting channel of: " + guild.getId());
        String id = config.getProperty(guild.getId());
        return Bootstrap.getJda().getVoiceChannelById(id);
    }
}
