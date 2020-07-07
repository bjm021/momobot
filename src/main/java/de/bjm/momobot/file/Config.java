package de.bjm.momobot.file;

import com.cedarsoftware.util.io.JsonWriter;
import de.bjm.momobot.Bootstrap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The Config class handles interactions with the config.json file on the fly!
 *
 * Each request for a config value reads the file so that changes can be applied on the fly and take effect without a restart.
 */
public class Config {

    /**
     * Easy handling of config key names! Each enum presents a key name in the root {@link JSONObject} of the config file.
     */
    public enum ConfigValue {
        DEBUG("debug"), TOKEN("token"), PREFIX("prefix");

        private String id;

        ConfigValue(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    File configFile;

    /**
     * Initializes the config.
     * This will create a default config file if no config file exists
     */
    public Config() {
        configFile = new File("." + File.separator + "config.json");
        if (!configFile.exists()) {

            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();

                JSONObject root = new JSONObject();
                root.put("token", "TOKEN-HERE");
                root.put("debug", true);
                root.put("prefix", "-");
                JSONArray voiceChannels = new JSONArray();
                root.put("voice-channels", voiceChannels);
                JSONArray admins = new JSONArray();
                root.put("admins", admins);
                JSONArray restrictedCommands = new JSONArray();
                restrictedCommands.put("addadmin");
                restrictedCommands.put("removeadmin");
                restrictedCommands.put("listadmins");
                restrictedCommands.put("setvc");
                restrictedCommands.put("setdebug");
                restrictedCommands.put("setprefix");
                root.put("restricted-commands", restrictedCommands);

                System.out.println("Writing config file...");

                writeJSONFile(root);
            } catch (IOException e) {
                System.err.println("An error occurred while creating the Config file");
                e.printStackTrace();
            }
        }
    }

    /**
     * Writes the value of a specific config key to the file
     *
     * @param id        The Kay name of the setting to change
     * @param value     The value to write to that key
     */
    public void setValue(ConfigValue id, String value) {
        try {
            JSONObject root = readJSONFile();

            root.put(id.getId(), value);

            writeJSONFile(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a value from the config.json file
     * @param id    The value's key name
     * @return      The value as string or null if an exception throws
     */
    public String getValue(ConfigValue id) {
        try {
            JSONObject root = readJSONFile();
            return root.get(id.getId()).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Adds a user to the admin list
     * @param user          The user to add
     * @return              true = Added user
     *                      false = user already admin
     * @throws IOException  if it files reading the config file
     */
    public boolean addAdmin(User user) throws IOException {
        JSONObject root = readJSONFile();
        JSONArray admins = root.getJSONArray("admins");
        if (!admins.toList().contains(user.getId())) {
            admins.put(user.getId());
            root.put("admins", admins);
            writeJSONFile(root);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a user to the admin list
     * @param user          The user to add
     * @return              true = if the user is removed
     *                      false = if the user is no admin
     * @throws IOException  if it files reading the config file
     */
    public boolean removeAdmin(User user) throws IOException {
        JSONObject root = readJSONFile();
        JSONArray admins = root.getJSONArray("admins");
        if (admins.toList().contains(user.getId())) {
            admins.remove(admins.toList().indexOf(user.getId()));
            root.put("admins", admins);
            writeJSONFile(root);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a user to the admin list
     * @param user          The user to add
     * @return              true = is admin
     *                      false = is no admin
     * @throws IOException  if it files reading the config file
     */
    public boolean isAdmin(User user) throws IOException {

        JSONObject root = readJSONFile();
        JSONArray admins = root.getJSONArray("admins");
        return admins.toList().contains(user.getId());

    }

    public List<User> getAdminList() throws IOException {
        JSONObject root = readJSONFile();
        JSONArray admins = root.getJSONArray("admins");

        List<User> output = new ArrayList<>();

        for (Object admin : admins) {
            User tmp = Bootstrap.getJda().getUserById((String) admin);
            output.add(tmp);
        }

        return output;
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

    public boolean isCommandRestricted(String cmd){
        try {
            JSONObject root = readJSONFile();
            JSONArray rest = root.getJSONArray("restricted-commands");
            return rest.toList().contains(cmd);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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
