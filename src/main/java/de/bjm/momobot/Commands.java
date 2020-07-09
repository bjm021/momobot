package de.bjm.momobot;

import de.bjm.momobot.controller.BotCommandHandler;
import de.bjm.momobot.controller.BotController;
import de.bjm.momobot.controller.BotControllerFactory;
import de.bjm.momobot.file.Config;
import de.bjm.momobot.music.MusicController;
import de.bjm.momobot.utils.Hentai;
import de.bjm.momobot.utils.MessageBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Commands implements BotController {

    private final Guild guild;

    public Commands(Guild guild) {
        this.guild = guild;
    }


    @BotCommandHandler
    private void testc(Message message) {
        message.getChannel().sendMessage("test successful").queue();
    }

    @BotCommandHandler
    private void setPrefix(Message message, String prefix) {
        Bootstrap.getConfig().setValue(Config.ConfigValue.PREFIX, prefix);
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Prefix set to " + prefix + "! EFFECTIVE IMMEDIATELY")).queue();
    }

    @BotCommandHandler
    public void getaurl(Message message, Long id) {
        System.out.println(id);
        User user = message.getJDA().getUserById(id);
        if (user != null) {
            message.getChannel().sendMessage(MessageBuilder.buildSuccess("AvatarUrl of " + user.getName() + " is: " + user.getAvatarUrl())).queue();
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("No user by that id!", null)).queue();
        }
    }

    @BotCommandHandler
    public void setaurl (Message message, String url) {
        try {
            URL test = new URL(url);
            URLConnection connection = test.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; U; Intel Mac OS X 10.4; en-US; rv:1.9.2.2) Gecko/20100316 Firefox/3.6.2");
            if (connection.getHeaderField("Content-Type").equalsIgnoreCase("image/png")) {
                message.getChannel().sendMessage(MessageBuilder.buildMessage("Attempting to update the Avatar...", Color.CYAN)).queue();
                message.getJDA().getSelfUser().getManager().setAvatar(Icon.from(connection.getInputStream())).complete();
                message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully updated the Avatar image! (It might take a while to update to your discord. Try pressing on the bot profile)")).queue();
            } else {
                message.getChannel().sendMessage(MessageBuilder.buildError("The Server said that your image is not a png!!! But it needs to be! (Server returned MIME type: " + connection.getHeaderField("Content-Type") + " but needs to be image/png)", null)).queue();
            }
        } catch (MalformedURLException e) {
            message.getChannel().sendMessage(MessageBuilder.buildError("This is a malformed URL/URI", null)).queue();
            return;
        } catch (IOException e) {
            message.getChannel().sendMessage(MessageBuilder.buildError("Something went wrong while getting your URL/URI", e)).queue();
        } catch (NullPointerException e) {
            //e.printStackTrace();
            message.getChannel().sendMessage(MessageBuilder.buildError("Seems like your URL/URI is not reachable", null)).queue();
        }

    }

    @BotCommandHandler
    private void copyright(Message message) {
        message.getChannel().sendMessage(MessageBuilder.buildMessage("Copyright (C) 2019-2020 Benjamin J. Meyer / BJM Development", Color.GREEN)).queue();
    }

    @BotCommandHandler
    private void listadmins(Message message) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Admin List");
        eb.setDescription("All listed users have admin permissions!");
        eb.setAuthor("MomoBot " + Bootstrap.VERSION, "https://momobot.cf", "https://cdn.discordapp.com/avatars/687607623650246677/b3676d9410b5af9a4527f216265b7441.png");
        try {
            Bootstrap.getConfig().getAdminList().forEach(user -> {
                eb.addField(user.getId(), user.getName(), false);
            });
            message.getChannel().sendMessage(eb.build()).queue();
        } catch (IOException e) {
            e.printStackTrace();
            MessageBuilder.ioError(e, message.getChannel());
        }

    }

    @BotCommandHandler
    private void addadmin(Message message, String id) {
        User user = message.getJDA().getUserById(id);
        if (user != null) {
            try {
                boolean result = Bootstrap.getConfig().addAdmin(user);
                if (result) {
                    message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully promoted " + user.getName() + " to admin")).queue();
                } else {
                    message.getChannel().sendMessage(MessageBuilder.buildError( user.getName() + " is already a admin!", null)).queue();
                }
            } catch (IOException e) {
                e.printStackTrace();
                MessageBuilder.ioError(e, message.getChannel());
            }
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("There is no user by that id", null)).queue();
        }
    }

    @BotCommandHandler
    private void setdebug(Message message, String bool) {
        boolean val = Boolean.parseBoolean(bool);
        Bootstrap.getConfig().setValue(Config.ConfigValue.DEBUG, Boolean.toString(val));
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully set DEBUG mode to " + val)).queue();
    }

    @BotCommandHandler
    private void rule34(Message message, int amount, String tags) {
        if (message.getTextChannel().isNSFW()) {
            System.out.println("HENTAI " + tags);
            String[] tagsSplit = tags.split(" ");
            java.util.List<String> tagsList = new ArrayList<>(Arrays.asList(tagsSplit));
            Hentai.hentai(Hentai.sites.RULE_34, tagsList, amount, message.getChannel());
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("Only ever do this in a NSFW channel!", null)).queue();
        }
    }

    @BotCommandHandler
    private void hentai(Message message, int amount, String tags) {
        rule34(message, amount, tags);
    }


    @BotCommandHandler
    private void real(Message message, int amount, String tags) {
        if (message.getTextChannel().isNSFW()) {
            System.out.println("HENTAI " + tags);
            String[] tagsSplit = tags.split(" ");
            java.util.List<String> tagsList = new ArrayList<>(Arrays.asList(tagsSplit));
            Hentai.hentai(Hentai.sites.REALBOORU, tagsList, amount, message.getChannel());
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("Only ever do this in a NSFW channel!", null)).queue();
        }
    }

    @BotCommandHandler
    private void safe(Message message, int amount, String tags) {
        if (message.getTextChannel().isNSFW()) {
            System.out.println("HENTAI " + tags);
            String[] tagsSplit = tags.split(" ");
            List<String> tagsList = new ArrayList<>(Arrays.asList(tagsSplit));
            Hentai.hentai(Hentai.sites.SAFEBOORU, tagsList, amount, message.getChannel());
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("Only ever do this in a NSFW channel!", null)).queue();
        }
    }

    @BotCommandHandler
    private void removeadmin(Message message, String id) {
        User user = message.getJDA().getUserById(id);
        if (user != null) {
            try {
                boolean result = Bootstrap.getConfig().removeAdmin(user);
                if (result) {
                    message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully demoted " + user.getName())).queue();
                } else {
                    message.getChannel().sendMessage(MessageBuilder.buildError( user.getName() + " is not even a admin!", null)).queue();
                }
            } catch (IOException e) {
                e.printStackTrace();
                MessageBuilder.ioError(e, message.getChannel());
            }
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("There is no user by that id", null)).queue();
        }
    }

    @BotCommandHandler
    private void help(Message message) {
        String prefix = Bootstrap.getConfig().getValue(Config.ConfigValue.PREFIX);
        if (prefix == null)
            prefix = "-";
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("HELP");
        eb.setColor(Color.BLUE);
        eb.setDescription("All Bot Commands, args in <arg> required | args in [arg] optional");
        eb.addField("", "Generic COMMANDS", false);
        eb.addField(prefix + "now <url>", "Plays a track instantly", true);
        eb.addField(prefix + "add <url>", "Adds a track to Queue", true);
        eb.addField(prefix + "pause", "Pauses the player", true);
        eb.addField(prefix + "resume", "Resumes after pause", true);
        eb.addField(prefix + "volume <value>", "Sets the audio volume", true);
        eb.addField(prefix + "clear", "Clears the queue", true);
        eb.addField("", "EQ COMMANDS", false);
        eb.addField(prefix + "eqstart", "Engages the Equalizer (auto when using eq commands)", true);
        eb.addField(prefix + "eqstop", "Disengages the Equalizer", true);
        eb.addField(prefix + "eqband <band> <value>", "Modify the EQ", true);
        eb.addField(prefix + "eqhighbass <value>", "Modify the high band bass", true);
        eb.addField(prefix + "bassboost", "BASSBOOST (EARRAPE!!!)", true);
        eb.addField(prefix + "eqlowbass <value>", "Modify the low band bass", true);
        eb.addField("", "NSFW Commands", false);
        eb.addField(prefix + "rule34 / " + prefix + "hentai <amount> [tags]", "Pulls Hentai images from Rule34", true);
        eb.addField(prefix + "real <amount> [tags]", "Pulls Real images from Realbooru", true);
        eb.addField(prefix + "safe <amount> [tags]", "Pulls SFW images from Safebooru", true);
        eb.addField("", "Bot Settings Commands", false);
        eb.addField(prefix + "setvc <channel_id>", "Sets the VoiceChannel the bot uses", true);
        eb.addField(prefix + "setdebug <true/false>", "Sets the DEBUG mode of the bot", true);
        eb.setAuthor("MomoBot " + Bootstrap.VERSION, "https://momobot.cf", "https://cdn.discordapp.com/avatars/687607623650246677/b3676d9410b5af9a4527f216265b7441.png");
        eb.setThumbnail("http://cdn.bjm.hesteig.com/BJM_Logo_white.png");
        message.getChannel().sendMessage(eb.build()).queue();

        eb = new EmbedBuilder();
        eb.setColor(Color.BLUE);
        eb.addField("", "Queue Management", false);
        eb.addField(prefix + "savequeue <name>", "Saves the last 8 queue items in a file", true);
        eb.addField(prefix + "listqueues", "List saved queues", true);
        eb.addField(prefix + "loadqueue <name>", "Loads all track of a saved queue by name", true);
        eb.addField("", "Administrative commands", false);
        eb.addField(prefix + "addadmin <id>", "Promote a user to admin", true);
        eb.addField(prefix + "removeadmin <id>", "Demote a user from admin", true);
        eb.addField(prefix + "listadmins", "List all admins", true);
        eb.addField("Hint", "To restrict commands add their names (without the prefix) to the config.json file! After that only admins have permission to execute these commands!", false);
        eb.addField("", "Miscellaneous commands", false);
        eb.addField(prefix + "setprefix <prefixChar>", "sets the prefix of the bot! EFFECTIVE IMMEDIATELY", true);
        eb.addField(prefix + "copyright", "Prints Copyright Information", true);
        eb.addField(prefix + "license", "Prints License Information", true);
        eb.addField(prefix + "setaurl <url>", "Updates the bot's avatar. May be Rate limited (Don't use too often)", true);
        eb.addField(prefix + "setusername <username>", "Sets the bot's username", true);
        //eb.setAuthor("MomoBot " + Bootstrap.VERSION, "https://momobot.cf", "https://cdn.discordapp.com/avatars/687607623650246677/b3676d9410b5af9a4527f216265b7441.png");
        eb.setFooter("MomoBot " + Bootstrap.VERSION + " based on lavaplayer | by b.jm021", "http://cdn.bjm.hesteig.com/BJM_Logo_white.png");
        message.getChannel().sendMessage(eb.build()).queue();



        //eb.setFooter("MomoBot " + Bootstrap.VERSION + " based on lavaplayer | by b.jm021", "http://cdn.bjm.hesteig.com/BJM_Logo_white.png");
        //eb.setThumbnail("http://cdn.bjm.hesteig.com/BJM_Logo_white.png");
    }

    @BotCommandHandler
    public void setUsername(Message message, String name) {
        message.getChannel().sendMessage(MessageBuilder.buildMessage("Attempting to update username...", Color.CYAN)).queue();
        message.getJDA().getSelfUser().getManager().setName(name).complete();
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully updated the bot name! (It might take a while to update to your discord. Try pressing on the bot profile)")).queue();
    }




    @BotCommandHandler
    private void license(Message message) {
        message.getChannel().sendMessage("\n" +
                "                      GNU GENERAL PUBLIC LICENSE\n" +
                "                       Version 3, 29 June 2007\n" +
                "\n" +
                " Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>\n" +
                " Everyone is permitted to copy and distribute verbatim copies\n" +
                " of this license document, but changing it is not allowed.\n" +
                "\n" +
                "                            Preamble\n" +
                "\n" +
                "  The GNU General Public License is a free, copyleft license for\n" +
                "software and other kinds of works.\n" +
                "\n" +
                "  The licenses for most software and other practical works are designed\n" +
                "to take away your freedom to share and change the works.  By contrast,\n" +
                "the GNU General Public License is intended to guarantee your freedom to\n" +
                "share and change all versions of a program--to make sure it remains free\n" +
                "software for all its users.  We, the Free Software Foundation, use the\n" +
                "GNU General Public License for most of our software; it applies also to\n" +
                "any other work released this way by its authors.  You can apply it to\n" +
                "your programs, too.\n" +
                "\n" +
                "  When we speak of free software, we are referring to freedom, not\n" +
                "price.  Our General Public Licenses are designed to make sure that you\n" +
                "have the freedom to distribute copies of free software (and charge for\n" +
                "them if you wish), that you receive source code or can get it if you\n" +
                "want it, that you can change the software or use pieces of it in new\n" +
                "free programs, and that you know you can do these things.\n").queue();
        message.getChannel().sendMessage("\n" +
                "  To protect your rights, we need to prevent others from denying you\n" +
                "these rights or asking you to surrender the rights.  Therefore, you have\n" +
                "certain responsibilities if you distribute copies of the software, or if\n" +
                "you modify it: responsibilities to respect the freedom of others.\n" +
                "\n" +
                "  For example, if you distribute copies of such a program, whether\n" +
                "gratis or for a fee, you must pass on to the recipients the same\n" +
                "freedoms that you received.  You must make sure that they, too, receive\n" +
                "or can get the source code.  And you must show them these terms so they\n" +
                "know their rights.\n" +
                "\n" +
                "  Developers that use the GNU GPL protect your rights with two steps:\n" +
                "(1) assert copyright on the software, and (2) offer you this License\n" +
                "giving you legal permission to copy, distribute and/or modify it.\n" +
                "\n" +
                "  For the developers' and authors' protection, the GPL clearly explains\n" +
                "that there is no warranty for this free software.  For both users' and\n" +
                "authors' sake, the GPL requires that modified versions be marked as\n" +
                "changed, so that their problems will not be attributed erroneously to\n" +
                "authors of previous versions.\n" +
                "\n" +
                "  Some devices are designed to deny users access to install or run\n" +
                "modified versions of the software inside them, although the manufacturer\n" +
                "can do so.  This is fundamentally incompatible with the aim of\n" +
                "protecting users' freedom to change the software.  The systematic\n" +
                "pattern of such abuse occurs in the area of products for individuals to").queue();
        message.getChannel().sendMessage("\n" +
                "use, which is precisely where it is most unacceptable.  Therefore, we\n" +
                "have designed this version of the GPL to prohibit the practice for those\n" +
                "products.  If such problems arise substantially in other domains, we\n" +
                "stand ready to extend this provision to those domains in future versions\n" +
                "of the GPL, as needed to protect the freedom of users.\n" +
                "\n" +
                "  Finally, every program is threatened constantly by software patents.\n" +
                "States should not allow patents to restrict development and use of\n" +
                "software on general-purpose computers, but in those that do, we wish to\n" +
                "avoid the special danger that patents applied to a free program could\n" +
                "make it effectively proprietary.  To prevent this, the GPL assures that\n" +
                "patents cannot be used to render the program non-free.\n" +
                "\n" +
                "  The precise terms and conditions for copying, distribution and\n" +
                "modification follow.\n" +
                "\n").queue();

        message.getChannel().sendMessage("For the complete License see here: https://github.com/bjm021/momobot/blob/master/LICENSE").queue();

        message.getChannel().sendMessage("\n" +
                "  15. Disclaimer of Warranty.\n" +
                "\n" +
                "  THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY\n" +
                "APPLICABLE LAW.  EXCEPT WHEN OTHERWISE STATED IN WRITING THE COPYRIGHT\n" +
                "HOLDERS AND/OR OTHER PARTIES PROVIDE THE PROGRAM \"AS IS\" WITHOUT WARRANTY\n" +
                "OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING, BUT NOT LIMITED TO,\n" +
                "THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR\n" +
                "PURPOSE.  THE ENTIRE RISK AS TO THE QUALITY AND PERFORMANCE OF THE PROGRAM\n" +
                "IS WITH YOU.  SHOULD THE PROGRAM PROVE DEFECTIVE, YOU ASSUME THE COST OF\n" +
                "ALL NECESSARY SERVICING, REPAIR OR CORRECTION.\n").queue();

        message.getChannel().sendMessage("\n" +
                "  16. Limitation of Liability.\n" +
                "\n" +
                "  IN NO EVENT UNLESS REQUIRED BY APPLICABLE LAW OR AGREED TO IN WRITING\n" +
                "WILL ANY COPYRIGHT HOLDER, OR ANY OTHER PARTY WHO MODIFIES AND/OR CONVEYS\n" +
                "THE PROGRAM AS PERMITTED ABOVE, BE LIABLE TO YOU FOR DAMAGES, INCLUDING ANY\n" +
                "GENERAL, SPECIAL, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE\n" +
                "USE OR INABILITY TO USE THE PROGRAM (INCLUDING BUT NOT LIMITED TO LOSS OF\n" +
                "DATA OR DATA BEING RENDERED INACCURATE OR LOSSES SUSTAINED BY YOU OR THIRD\n" +
                "PARTIES OR A FAILURE OF THE PROGRAM TO OPERATE WITH ANY OTHER PROGRAMS),\n" +
                "EVEN IF SUCH HOLDER OR OTHER PARTY HAS BEEN ADVISED OF THE POSSIBILITY OF\n" +
                "SUCH DAMAGES.").queue();
    }







    public static class Factory implements BotControllerFactory<Commands> {
        @Override
        public Class<Commands> getControllerClass() {
            return Commands.class;
        }

        @Override
        public Commands create(BotApplicationManager manager, BotGuildContext state, Guild guild) {
            return new Commands(guild);
        }
    }

}
