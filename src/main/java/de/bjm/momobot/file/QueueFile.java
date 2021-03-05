package de.bjm.momobot.file;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonWriter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;

/**
 *
 *     Copyright (C) 2020-2021 BJM SoftwareWorks (Benjamin J. Meyer)
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
 *
 * QueueFile
 *
 * template:
 *
 * {
 * 	"queues": [{
 * 			"name": "test",
 * 			"items": [
 * 				"item1", "item2"
 * 			]
 *                },
 *        {
 * 			"name": "test2",
 * 			"items": [
 * 				"item1", "item2"
 * 			]
 *        }
 * 	]
 * }
 */
public class QueueFile {

    private Exception latestException;
    private final File queueFile;
    private static final String path = "." + File.separator + "queues.json";

    public Exception getLatestException() {
        return latestException;
    }

    public QueueFile() {
        queueFile = new File(path);
        if (!queueFile.exists()) {
            createFile();
            System.out.println("CREATED QUEUE FILE");
        }
    }

    private void createFile() {
        JSONObject root = new JSONObject();
        JSONArray queues = new JSONArray();
        root.put("queues", queues);

        try {
            queueFile.getParentFile().mkdirs();
            queueFile.createNewFile();

            FileWriter writer = new FileWriter(path);
            writer.write(root.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves a queue to the save-file
     * @param name  The name to save the queue
     * @return The status code of the operation
     *          0 : Success
     *          1 : Duplicate Name
     *          -1 : Unexpected Error
     */
    public int addQueueToFile(String name, BlockingDeque<AudioTrack> queue) {
        try {
            InputStream is = new FileInputStream(queueFile);
            JSONObject root = new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));
            JSONArray queues = root.getJSONArray("queues");

            List<String> existingNames = new ArrayList<>();

            if (queues.length() > 0) {
                queues.forEach(o -> {
                    JSONObject tmp = (JSONObject) o;
                    existingNames.add(tmp.getString("name"));
                });
            }

            if (existingNames.contains(name)) {
                return 1;
            }

            JSONObject tmpQueue = new JSONObject();
            tmpQueue.put("name", name);
            JSONArray items = new JSONArray();

            for (AudioTrack a : queue) {
                items.put(a.getIdentifier());
            }

            tmpQueue.put("items", items);

            queues.put(tmpQueue);

            writeFile(root);

            return 0;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            latestException = e;
            return -1;
        } catch (IOException e) {
            e.printStackTrace();
            latestException = e;
            return -1;
        }
    }

    public List<String> listQueues() {
        try {
            InputStream is = new FileInputStream(queueFile);
            JSONObject root = new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));

            JSONArray queues = root.getJSONArray("queues");

            if (queues.length() == 0)
                return null;


            List<String> output = new ArrayList<>();
            queues.forEach(o -> {
                JSONObject tmp = (JSONObject) o;
                output.add("- " + tmp.getString("name"));
            });
            return output;
        } catch (Exception e) {
            e.printStackTrace();
            latestException = e;
        }
        return null;
    }

    public List<String> getQueueItems(String name) {
        try {
            List<String> output = null;

            InputStream is = new FileInputStream(queueFile);
            JSONObject root = new JSONObject(IOUtils.toString(is, StandardCharsets.UTF_8));
            JSONArray queues = root.getJSONArray("queues");

            if (queues.length() == 0)
                return null;

            for (Object queue : queues) {
                JSONObject tmp = (JSONObject) queue;
                if (tmp.getString("name").equalsIgnoreCase(name)) {
                    output = new ArrayList<>();
                    JSONArray items = tmp.getJSONArray("items");
                    if (items.length() == 0)
                        return null;

                    for (Object item : items) {
                        output.add(item.toString());
                    }

                    return output;
                }
            }

            return null;
        } catch (IOException e) {
            latestException = e;
            e.printStackTrace();
        }

        return null;
    }

    private void writeFile(JSONObject o) {
        try {
            FileWriter writer = new FileWriter(queueFile);
            writer.write(JsonWriter.formatJson(o.toString()));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
