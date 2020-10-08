package de.bjm.momobot.utils;

import com.cedarsoftware.util.io.JsonWriter;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class TWOLogic {

    private static final File configFile = new File("." + File.separator + "SuperGeheimerConfigFile2.json");

    private List<String> activeGuilds = new ArrayList<>();
    private List<String> catchPhrases = new ArrayList<>();
    private List<String> answers = new ArrayList<>();

    private boolean active = true;

    public TWOLogic() {
        if (!configFile.exists()) {
            createFile();
        }

        JSONObject root = readFile();
        active = root.getBoolean("2");
        System.out.println("[momobot] [2]: " + active);
        JSONArray guilds = root.getJSONArray("guilds");
        JSONArray answers = root.getJSONArray("answers");
        guilds.toList().forEach(o -> {
            activeGuilds.add(o.toString());
            //try {
            //    activeGuilds.p
            //} catch (NullPointerException e) {
            //    System.err.println("[MomoBot] [2] Error! Guild by id: " + o.toString() + " does not exist!!!");
            //    }
        });
        answers.toList().forEach(o -> {
            this.answers.add(o.toString());
        });
        JSONArray catchPhrases = root.getJSONArray("catchphrases");
        catchPhrases.forEach(o -> {
            this.catchPhrases.add(o.toString());
        });

    }

    private void createFile() {
        JSONObject root = new JSONObject();
        root.put("2", true);
        JSONArray guildIDs = new JSONArray();
        JSONArray catchphrases = new JSONArray();
        JSONArray answers = new JSONArray();
        answers.put("Zwei?????");
        answers.put("2 ?????");
        catchphrases.put(" 2 ");
        catchphrases.put("2");
        catchphrases.put(" zwei ");
        catchphrases.put("zwei");
        catchphrases.put(" two ");
        catchphrases.put("two");
        root.put("catchphrases", catchphrases);
        root.put("answers", answers);
        root.put("guilds", guildIDs);

        try {
            FileWriter writer = new FileWriter(configFile);
            writer.write(JsonWriter.formatJson(root.toString()));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private JSONObject readFile() {
        try {
            InputStream is = new FileInputStream(configFile);
            return new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void handleEvent(MessageReceivedEvent event) {
        if (!active) {
            return;
        }

        String content = event.getMessage().getContentDisplay();

        AtomicBoolean sendTWO = new AtomicBoolean(false);

        catchPhrases.forEach(s -> {
            if (content.toLowerCase().contains(s.toLowerCase())) {
                sendTWO.set(true);
            }
        });

        System.out.println("sent guild: " + event.getGuild().getId());
        System.out.println("active guild: " + activeGuilds.get(0));
        activeGuilds.forEach(s -> {
            if (event.getGuild().getId().equalsIgnoreCase(s)) {
                if (sendTWO.get()) {
                    String answerText = answers.get(ThreadLocalRandom.current().nextInt(0, answers.size()));


                    event.getChannel().sendMessage(answerText).queue();
                }
            }
        });
    }
}
