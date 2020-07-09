package de.bjm.momobot.utils;

import com.cedarsoftware.util.io.JsonWriter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class Setup {

    public Setup() {
        File configFile = new File("." + File.separator + "config.json");

        Scanner in = new Scanner(System.in);

        System.out.println("Welcome to the setup!\nThis will create a config file that works with your credentials!\n\nPlease enter your token (https://discord.com/developers/applications)");
        System.out.print("> ");

        String token = in.next();
        JSONObject root = new JSONObject();
        root.put("token", token);

        System.out.println("Do you want to enable the debug mode (not recommended) (Will spam debug messaged) (y/n)");
        System.out.print("> ");
        String check = in.next();
        if (check.equalsIgnoreCase("y")) {
            System.out.println("Enabling debug mode...");
            root.put("debug", true);
        } else {
            System.out.println("Disabling debug mode...");
            root.put("debug", false);
        }

        System.out.println("\nSetup the bot command prefix\nby default this is - \nDo you want to change this? (y/n)");
        System.out.print("> ");
        check = in.next();
        if (check.equalsIgnoreCase("y")) {
            System.out.println("Please enter a new prefix...");
            System.out.print("> ");
            String prefix = in.next();
            root.put("prefix", prefix);
            System.out.println("New prefix is set to: \"" + prefix + "\"");
        } else {
            System.out.println("OK! Prefix is now \"-\"...");
            root.put("prefix", "-");
        }

        JSONArray voiceChannels = new JSONArray();
        root.put("voice-channels", voiceChannels);
        
        
        System.out.println("\nAdmin setup\nPlease enter your discord user id to add yourself as an admin!");
        System.out.print("> ");
        JSONArray admins = new JSONArray();
        admins.put(in.next());
        root.put("admins", admins);

        System.out.println("\nDefault restricted commands!\nYou can configure which commands shall be restricted to admin use only\nDo you want to restrict the standard commands? (y/n)");
        System.out.print("> ");


        JSONArray restrictedCommands = new JSONArray();
        check = in.next();
        if (check.equalsIgnoreCase("y")) {
            System.out.println("Adding restricted commands!");
            restrictedCommands.put("addadmin");
            restrictedCommands.put("removeadmin");
            restrictedCommands.put("listadmins");
            restrictedCommands.put("setvc");
            restrictedCommands.put("setdebug");
            restrictedCommands.put("setprefix");
            restrictedCommands.put("setaurl");
            restrictedCommands.put("setusername");
        } else {
            System.out.println("Don't adding restricted commands! (RISKY!!!)");
        }



        root.put("restricted-commands", restrictedCommands);

        System.out.println("\n\nFinished Setup, writing config...");

        try {
            FileWriter writer = new FileWriter(configFile);
            writer.write(JsonWriter.formatJson(root.toString()));
            writer.close();
            System.out.println("Finished Setup! Press any key to exit!");
            in.nextLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
