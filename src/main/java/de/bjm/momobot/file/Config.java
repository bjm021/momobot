package de.bjm.momobot.file;

import com.cedarsoftware.util.io.JsonWriter;
import de.bjm.momobot.Bootstrap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Config {

    public enum ConfigValue {
        DEBUG("debug"), TOKEN("token");

        private String id;

        ConfigValue(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    File configFile;

    public Config() {
        configFile = new File("." + File.separator + "config.json");
        if (!configFile.exists()) {

            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();

                JSONObject root = new JSONObject();
                root.put("token", "TOKEN-HERE");
                root.put("debug", true);
                JSONArray voiceChannels = new JSONArray();
                root.put("voice-channels", voiceChannels);

                writeJSONFile(root);
            } catch (IOException e) {
                System.err.println("An error occurred while creating the Config file");
                e.printStackTrace();
            }
        }
    }

    public void setValue(ConfigValue id, String value) {
        try {
            JSONObject root = readJSONFile();

            root.put(id.getId(), value);

            writeJSONFile(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getValue(ConfigValue id) {
        try {
            JSONObject root = readJSONFile();
            return root.get(id.getId()).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }



    private JSONObject readJSONFile() throws IOException {
        InputStream is = new FileInputStream(configFile);
        return new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));
    }

    private void writeJSONFile(JSONObject root) {
        try {
            FileWriter writer = new FileWriter(configFile);
            writer.write(JsonWriter.formatJson(root.toString()));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setVoiceChannelToUse(Guild guild, String id) {
        try {
            JSONObject root = readJSONFile();
            JSONArray voiceChannels = root.getJSONArray("voice-channels");
            JSONObject entry = new JSONObject();
            entry.put("guild", guild.getId());
            entry.put("use-channel", id);
            voiceChannels.put(entry);
            root.put("voice-channels", voiceChannels);
            writeJSONFile(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public VoiceChannel getVoiceChannelToUse(Guild guild) {
        try {
            JSONObject root = readJSONFile();
            JSONArray channels = root.getJSONArray("voice-channels");
            for (Object channel : channels) {
                JSONObject tmpJSON = (JSONObject) channel;
                if (tmpJSON.getString("guild").equalsIgnoreCase(guild.getId())) {
                    return guild.getJDA().getVoiceChannelById(tmpJSON.getString("use-channel"));
                }

                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return null;
    }


}

/*
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
        if (id == null)
            return null;
        return Bootstrap.getJda().getVoiceChannelById(id);
    }

    public void setValue(ConfigValue key, String value) {
        readFile();
        config.setProperty(key.toString(), value);
        updateFile();
    }

    public void setVoiceChannelToUse(Guild guild, String id) {
        readFile();
        config.setProperty(guild.getId(), id);
        updateFile();
    }
 */
