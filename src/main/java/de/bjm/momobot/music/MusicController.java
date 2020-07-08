package de.bjm.momobot.music;

import com.sedmelluq.discord.lavaplayer.filter.equalizer.EqualizerFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.remote.RemoteNode;
import com.sedmelluq.discord.lavaplayer.remote.message.NodeStatisticsMessage;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.PlayerLibrary;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageInput;
import com.sedmelluq.discord.lavaplayer.tools.io.MessageOutput;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DecodedTrackHolder;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import de.bjm.momobot.Bootstrap;
import de.bjm.momobot.BotApplicationManager;
import de.bjm.momobot.BotGuildContext;
import de.bjm.momobot.MessageDispatcher;
import de.bjm.momobot.controller.BotCommandHandler;
import de.bjm.momobot.controller.BotController;
import de.bjm.momobot.controller.BotControllerFactory;
import de.bjm.momobot.file.Config;
import de.bjm.momobot.utils.MessageBuilder;
import de.bjm.momobot.utils.Hentai;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.managers.AudioManager;
import net.iharder.Base64;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@SuppressWarnings("DuplicatedCode")
public class MusicController implements BotController {
    private static final float[] BASS_BOOST = {0.2f, 0.15f, 0.1f, 0.05f, 0.0f, -0.05f, -0.1f, -0.1f, -0.1f, -0.1f, -0.1f,
            -0.1f, -0.1f, -0.1f, -0.1f};

    private final AudioPlayerManager manager;
    private final AudioPlayer player;
    private final AtomicReference<TextChannel> outputChannel;
    private final MusicScheduler scheduler;
    private final MessageDispatcher messageDispatcher;
    private final Guild guild;
    private final EqualizerFactory equalizer;

    public static String latestURI;
    public static boolean repeat = false;

    public MusicController(BotApplicationManager manager, BotGuildContext state, Guild guild) {
        this.manager = manager.getPlayerManager();
        this.guild = guild;
        this.equalizer = new EqualizerFactory();

        player = manager.getPlayerManager().createPlayer();
        guild.getAudioManager().setSendingHandler(new AudioPlayerSendHandler(player));

        outputChannel = new AtomicReference<>();

        messageDispatcher = new GlobalDispatcher();
        scheduler = new MusicScheduler(player, messageDispatcher, manager.getExecutorService());

        player.addListener(scheduler);
    }

    @BotCommandHandler
    private void add(Message message, String identifier) {
        addTrack(message, identifier, false);
    }

    @BotCommandHandler
    private void now(Message message, String identifier) {
        addTrack(message, identifier, true);
    }

    @BotCommandHandler
    private void hex(Message message, int pageCount) {
        manager.source(YoutubeAudioSourceManager.class).setPlaylistPageCount(pageCount);
    }

    @BotCommandHandler
    private void serialize(Message message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MessageOutput outputStream = new MessageOutput(baos);

        for (AudioTrack track : scheduler.drainQueue()) {
            manager.encodeTrack(outputStream, track);
        }

        outputStream.finish();

        message.getChannel().sendMessage(Base64.encodeBytes(baos.toByteArray())).queue();
    }

    @BotCommandHandler
    private void deserialize(Message message, String content) throws IOException {
        outputChannel.set((TextChannel) message.getChannel());
        connectToFirstVoiceChannel(guild.getAudioManager());

        byte[] bytes = Base64.decode(content);

        MessageInput inputStream = new MessageInput(new ByteArrayInputStream(bytes));
        DecodedTrackHolder holder;

        while ((holder = manager.decodeTrack(inputStream)) != null) {
            if (holder.decodedTrack != null) {
                scheduler.addToQueue(holder.decodedTrack);
            }
        }
    }

    @BotCommandHandler
    private void eqsetup(Message message) {
        manager.getConfiguration().setFilterHotSwapEnabled(true);
        player.setFrameBufferDuration(500);
    }

    @BotCommandHandler
    private void eqstart(Message message) {
        player.setFilterFactory(equalizer);
    }

    @BotCommandHandler
    private void eqstop(Message message) {
        player.setFilterFactory(null);
    }

    @BotCommandHandler
    private void eqband(Message message, int band, float value) {
        player.setFilterFactory(equalizer);
        equalizer.setGain(band, value);
    }

    @BotCommandHandler
    private void eqhighbass(Message message, float diff) {
        player.setFilterFactory(equalizer);
        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, BASS_BOOST[i] + diff);
        }
    }

    @BotCommandHandler
    private void eqlowbass(Message message, float diff) {
        player.setFilterFactory(equalizer);
        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, -BASS_BOOST[i] + diff);
        }
    }

    @BotCommandHandler
    private void bassboost(Message message) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.RED);
        eb.setDescription("ENGAGING BASSBOOST! PREPARE FOR EARRAPE!!!");
        message.getChannel().sendMessage(eb.build()).queue();
        player.setFilterFactory(equalizer);
        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, BASS_BOOST[i] + 2);
        }

        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, -BASS_BOOST[i] + 1);
        }
    }

    @BotCommandHandler
    private void volume(Message message, int volume) {
        player.setVolume(volume);
    }

    @BotCommandHandler
    private void nodes(Message message, String addressList) {
        manager.useRemoteNodes(addressList.split(" "));
    }

    @BotCommandHandler
    private void local(Message message) {
        manager.useRemoteNodes();
    }

    @BotCommandHandler
    private void skip(Message message) {
        scheduler.skip();
    }

    @BotCommandHandler
    private void forward(Message message, int duration) {
        forPlayingTrack(track -> track.setPosition(track.getPosition() + duration));
    }

    @BotCommandHandler
    private void back(Message message, int duration) {
        forPlayingTrack(track -> track.setPosition(Math.max(0, track.getPosition() - duration)));
    }

    @BotCommandHandler
    private void pause(Message message) {
        player.setPaused(true);
    }

    @BotCommandHandler
    private void resume(Message message) {
        player.setPaused(false);
    }

    @BotCommandHandler
    private void duration(Message message) {
        forPlayingTrack(track -> message.getChannel().sendMessage("Duration is " + track.getDuration()).queue());
    }

    @BotCommandHandler
    private void seek(Message message, long position) {
        forPlayingTrack(track -> track.setPosition(position));
    }

    @BotCommandHandler
    private void pos(Message message) {
        forPlayingTrack(track -> message.getChannel().sendMessage("Position is " + track.getPosition()).queue());
    }

    @BotCommandHandler
    private void marker(final Message message, long position, final String text) {
        forPlayingTrack(track -> track.setMarker(new TrackMarker(position, state -> message.getChannel().sendMessage("Trigger [" + text + "] cause [" + state.name() + "]").queue())));
    }

    @BotCommandHandler
    private void unmark(Message message) {
        forPlayingTrack(track -> track.setMarker(null));
    }

    @BotCommandHandler
    private void version(Message message) {
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Running momobot " + Bootstrap.VERSION + " (using PlayerLibrary " + PlayerLibrary.VERSION + ")")).queue();
    }

    @BotCommandHandler
    private void nodeinfo(Message message) {
        for (RemoteNode node : manager.getRemoteNodeRegistry().getNodes()) {
            String report = buildReportForNode(node);
            message.getChannel().sendMessage(report).queue();
        }
    }

    @BotCommandHandler
    private void provider(Message message) {
        forPlayingTrack(track -> {
            RemoteNode node = manager.getRemoteNodeRegistry().getNodeUsedForTrack(track);

            if (node != null) {
                message.getChannel().sendMessage("Node " + node.getAddress()).queue();
            } else {
                message.getChannel().sendMessage("Not played by a remote node.").queue();
            }
        });
    }

    @BotCommandHandler
    private void leave(Message message) {
        guild.getAudioManager().closeAudioConnection();
    }

    @BotCommandHandler
    private void stop(Message message) {
        player.setPaused(true);
        guild.getAudioManager().closeAudioConnection();
    }

    @BotCommandHandler
    private void clear(Message message) {
        scheduler.clearQueue();
        message.getChannel().sendMessage("Queue cleared!").queue();
    }

    @BotCommandHandler
    private void rule34(Message message, int amount, String tags) {
        if (message.getTextChannel().isNSFW()) {
            System.out.println("HENTAI " + tags);
            String[] tagsSplit = tags.split(" ");
            List<String> tagsList = new ArrayList<>(Arrays.asList(tagsSplit));
            Hentai.hentai(Hentai.sites.RULE_34, tagsList, amount, message.getChannel());
        } else {
            message.getChannel().sendMessage("ONLY do this in NSFW channels!!!").queue();
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
            List<String> tagsList = new ArrayList<>(Arrays.asList(tagsSplit));
            Hentai.hentai(Hentai.sites.REALBOORU, tagsList, amount, message.getChannel());
        } else {
            message.getChannel().sendMessage("ONLY do this in NSFW channels!!!").queue();
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
            message.getChannel().sendMessage("ONLY do this in NSFW channels!!!").queue();
        }
    }

    @BotCommandHandler
    private void setvc(Message message, String id) {
        try {
            VoiceChannel vc = message.getGuild().getVoiceChannelById(id);
            Bootstrap.getConfig().setVoiceChannelToUse(message.getGuild(), vc.getId());
            message.getChannel().sendMessage(MessageBuilder.buildSuccess("Active Bot Channel for Guild " + message.getGuild().getName() + " now set to " + vc.getName() + "#" + vc.getId())).queue();
        } catch (NumberFormatException e) {
            message.getChannel().sendMessage(MessageBuilder.buildError("Please enter a valid channel id! (NUMERIC)", e)).queue();
        } catch (NullPointerException e) {
            message.getChannel().sendMessage(MessageBuilder.buildError("No Voice Channel found by that id!", e)).queue();
        } catch (Exception e) {
            message.getChannel().sendMessage(MessageBuilder.buildError("Something went wrong! (check your id)", e)).queue();
        }
    }

    @BotCommandHandler
    private void setdebug(Message message, String bool) {
      boolean val = Boolean.parseBoolean(bool);
      Bootstrap.getConfig().setValue(Config.ConfigValue.DEBUG, Boolean.toString(val));
      message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully set DEBUG mode to " + val)).queue();
    }

    @BotCommandHandler
    private void listqueues(Message message) {
        List<String> queues = Bootstrap.getQueueFile().listQueues();
        if (queues != null) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setDescription("List of all Queues saved in save-file");
            queues.forEach(s -> eb.addField("", s, true));
            message.getChannel().sendMessage(eb.build()).queue();
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildError("Queues save-File may be empty", null)).queue();
        }
    }

    @BotCommandHandler
    private void savequeue(Message message, String name) {
        message.getChannel().sendMessage("Trying to save queue to file").queue();

        int result = Bootstrap.getQueueFile().addQueueToFile(name, scheduler.getQueue());
        switch (result) {
            case 0:
                message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully saved queue to file")).queue();
                break;
            case 1:
                message.getChannel().sendMessage(MessageBuilder.buildError("Queue by this name already exists in save file", null)).queue();
                break;
            case -1:
                message.getChannel().sendMessage(MessageBuilder.buildError("Something went wrong!", Bootstrap.getQueueFile().getLatestException())).queue();
                break;
        }
    }

    @BotCommandHandler
    private void loadqueue(Message message, String name) {
        List<String> items = Bootstrap.getQueueFile().getQueueItems(name);

        if (items == null) {
            message.getChannel().sendMessage(MessageBuilder.buildError("Either a saved queue ba that name does not exist or it has no entries", null)).queue();
            return;
        }

        scheduler.clearQueue();

        for (String item : items) {
            addTrack(message, item, false);
        }
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Loaded queue successfully")).queue();
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
        private void repeattrack(Message message) {

        if (player.getPlayingTrack() == null) {
            message.getChannel().sendMessage(MessageBuilder.buildError("There is currently no track playing", null)).queue();
            return;
        }

        manager.loadItemOrdered(this, player.getPlayingTrack().getInfo().uri, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                connectToFirstVoiceChannel(guild.getAudioManager());

                message.getChannel().sendMessage("Added : " + track.getInfo().title + " (length " + track.getDuration() + ") to the top of the queue").queue();

                scheduler.getQueue().addFirst(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<AudioTrack> tracks = playlist.getTracks();
                message.getChannel().sendMessage("Loaded playlist: " + playlist.getName() + " (" + tracks.size() + ")").queue();

                connectToFirstVoiceChannel(guild.getAudioManager());

                AudioTrack selected = playlist.getSelectedTrack();

                if (selected != null) {
                    message.getChannel().sendMessage("Selected track from playlist: " + selected.getInfo().title).queue();
                } else {
                    selected = tracks.get(0);
                    message.getChannel().sendMessage("Added first track from playlist: " + selected.getInfo().title + " to the top of queue!").queue();
                }

                scheduler.getQueue().addFirst(selected);

            }


            @Override
            public void noMatches() {
                //message.getChannel().sendMessage("Nothing found for " + identifier).queue();
            }

            @Override
            public void loadFailed(FriendlyException throwable) {
                message.getChannel().sendMessage("Failed with message: " + throwable.getMessage() + " (" + throwable.getClass().getSimpleName() + ")").queue();
            }
        });

    }

    /*
    @BotCommandHandler
    private void repeat(Message message) {
        if (!repeat) {
            repeat = true;
            message.getChannel().sendMessage(MessageBuilder.buildSuccess("Set repeat track to ON")).queue();
        } else {
            message.getChannel().sendMessage(MessageBuilder.buildSuccess("Set repeat track to OFF")).queue();
        }
    }
    */

    @BotCommandHandler
    private void geturi(Message message) {
        if (player.getPlayingTrack() == null) {
            message.getChannel().sendMessage(MessageBuilder.buildError("There is currently no track playing", null)).queue();
            return;
        }

        message.getChannel().sendMessage(player.getPlayingTrack().getInfo().uri).queue();
    }

    @BotCommandHandler
    private void copyright(Message message) {
        message.getChannel().sendMessage(MessageBuilder.buildMessage("Copyright (C) 2019-2020 Benjamin J. Meyer / BJM Development", Color.GREEN)).queue();
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
    public void setUsername(Message message, String name) {
        message.getChannel().sendMessage(MessageBuilder.buildMessage("Attempting to update username...", Color.CYAN)).queue();
        message.getJDA().getSelfUser().getManager().setName(name).complete();
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Successfully updated the bot name! (It might take a while to update to your discord. Try pressing on the bot profile)")).queue();
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

    private String buildReportForNode(RemoteNode node) {
        StringBuilder builder = new StringBuilder();
        builder.append("--- ").append(node.getAddress()).append(" ---\n");
        builder.append("Connection state: ").append(node.getConnectionState()).append("\n");

        NodeStatisticsMessage statistics = node.getLastStatistics();
        builder.append("Node global statistics: \n").append(statistics == null ? "unavailable" : "");

        if (statistics != null) {
            builder.append("   playing tracks: ").append(statistics.playingTrackCount).append("\n");
            builder.append("   total tracks: ").append(statistics.totalTrackCount).append("\n");
            builder.append("   system CPU usage: ").append(statistics.systemCpuUsage).append("\n");
            builder.append("   process CPU usage: ").append(statistics.processCpuUsage).append("\n");
        }

        builder.append("Minimum tick interval: ").append(node.getTickMinimumInterval()).append("\n");
        builder.append("Tick history capacity: ").append(node.getTickHistoryCapacity()).append("\n");

        List<RemoteNode.Tick> ticks = node.getLastTicks(false);
        builder.append("Number of ticks in history: ").append(ticks.size()).append("\n");

        if (ticks.size() > 0) {
            int tail = Math.min(ticks.size(), 3);
            builder.append("Last ").append(tail).append(" ticks:\n");

            for (int i = ticks.size() - tail; i < ticks.size(); i++) {
                RemoteNode.Tick tick = ticks.get(i);

                builder.append("   [duration ").append(tick.endTime - tick.startTime).append("]\n");
                builder.append("   start time: ").append(tick.startTime).append("\n");
                builder.append("   end time: ").append(tick.endTime).append("\n");
                builder.append("   response code: ").append(tick.responseCode).append("\n");
                builder.append("   request size: ").append(tick.requestSize).append("\n");
                builder.append("   response size: ").append(tick.responseSize).append("\n");
            }
        }

        List<AudioTrack> tracks = node.getPlayingTracks();

        builder.append("Number of playing tracks: ").append(tracks.size()).append("\n");

        if (tracks.size() > 0) {
            int head = Math.min(tracks.size(), 3);
            builder.append("First ").append(head).append(" tracks:\n");

            for (int i = 0; i < head; i++) {
                AudioTrack track = tracks.get(i);

                builder.append("   [identifier ").append(track.getInfo().identifier).append("]\n");
                builder.append("   name: ").append(track.getInfo().author).append(" - ").append(track.getInfo().title).append("\n");
                builder.append("   progress: ").append(track.getPosition()).append(" / ").append(track.getDuration()).append("\n");
            }
        }

        builder.append("Balancer penalties: ").append(tracks.size()).append("\n");

        for (Map.Entry<String, Integer> penalty : node.getBalancerPenaltyDetails().entrySet()) {
            builder.append("   ").append(penalty.getKey()).append(": ").append(penalty.getValue()).append("\n");
        }

        return builder.toString();
    }

    private void addTrack(final Message message, final String identifier, final boolean now) {
        outputChannel.set((TextChannel) message.getChannel());

        if (identifier.contains("http")) {

            manager.loadItemOrdered(this, identifier, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    connectToFirstVoiceChannel(guild.getAudioManager());

                    message.getChannel().sendMessage("Starting now: " + track.getInfo().title + " (length " + track.getDuration() + ")").queue();

                    if (now) {
                        scheduler.playNow(track, true);
                    } else {
                        scheduler.addToQueue(track);
                    }

                    latestURI = track.getInfo().uri;
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    List<AudioTrack> tracks = playlist.getTracks();
                    message.getChannel().sendMessage("Loaded playlist: " + playlist.getName() + " (" + tracks.size() + ")").queue();

                    connectToFirstVoiceChannel(guild.getAudioManager());

                    AudioTrack selected = playlist.getSelectedTrack();

                    if (selected != null) {
                        message.getChannel().sendMessage("Selected track from playlist: " + selected.getInfo().title).queue();
                    } else {
                        selected = tracks.get(0);
                        message.getChannel().sendMessage("Added first track from playlist: " + selected.getInfo().title).queue();
                    }

                    if (now) {
                        scheduler.playNow(selected, true);
                    } else {
                        scheduler.addToQueue(selected);
                    }

                    latestURI = selected.getInfo().uri;

                    for (int i = 0; i < Math.min(10, playlist.getTracks().size()); i++) {
                        if (tracks.get(i) != selected) {
                            scheduler.addToQueue(tracks.get(i));
                        }
                    }
                }


                @Override
                public void noMatches() {
                    message.getChannel().sendMessage("Nothing found for " + identifier).queue();
                }

                @Override
                public void loadFailed(FriendlyException throwable) {
                    message.getChannel().sendMessage("Failed with message: " + throwable.getMessage() + " (" + throwable.getClass().getSimpleName() + ")").queue();
                }
            });
        } else {
            if(identifier.contains("local:")) {
                String[] identifierSplit = identifier.split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < identifierSplit.length; i++) {
                    sb.append(identifierSplit[i]);
                    sb.append(" ");
                }

                message.getChannel().sendMessage("trying to get: " + sb.toString()).queue();

                manager.loadItemOrdered(this, sb.toString(), new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        connectToFirstVoiceChannel(guild.getAudioManager());

                        message.getChannel().sendMessage("Starting now: " + track.getInfo().title + " (length " + track.getDuration() + ")").queue();

                        if (now) {
                            scheduler.playNow(track, true);
                        } else {
                            scheduler.addToQueue(track);
                        }
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        List<AudioTrack> tracks = playlist.getTracks();
                        message.getChannel().sendMessage("Loaded playlist: " + playlist.getName() + " (" + tracks.size() + ")").queue();

                        connectToFirstVoiceChannel(guild.getAudioManager());

                        AudioTrack selected = playlist.getSelectedTrack();

                        if (selected != null) {
                            message.getChannel().sendMessage("Selected track from playlist: " + selected.getInfo().title).queue();
                        } else {
                            selected = tracks.get(0);
                            message.getChannel().sendMessage("Added first track from playlist: " + selected.getInfo().title).queue();
                        }

                        if (now) {
                            scheduler.playNow(selected, true);
                        } else {
                            scheduler.addToQueue(selected);
                        }

                        for (int i = 0; i < Math.min(10, playlist.getTracks().size()); i++) {
                            if (tracks.get(i) != selected) {
                                scheduler.addToQueue(tracks.get(i));
                            }
                        }
                    }


                    @Override
                    public void noMatches() {
                        message.getChannel().sendMessage("Nothing found for " + identifier).queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException throwable) {
                        message.getChannel().sendMessage("Failed with message: " + throwable.getMessage() + " (" + throwable.getClass().getSimpleName() + ")").queue();
                    }
                });
            } else {
                System.out.println("NO HTTP DETECTED USING YT SEARCH");

                manager.loadItemOrdered(this, "ytsearch: " + identifier, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        connectToFirstVoiceChannel(guild.getAudioManager());

                        message.getChannel().sendMessage("Starting now: " + track.getInfo().title + " (length " + track.getDuration() + ")").queue();

                        if (now) {
                            scheduler.playNow(track, true);
                        } else {
                            scheduler.addToQueue(track);
                        }
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        List<AudioTrack> tracks = playlist.getTracks();
                        message.getChannel().sendMessage("Loaded playlist: " + playlist.getName() + " (" + tracks.size() + ")").queue();

                        connectToFirstVoiceChannel(guild.getAudioManager());

                        AudioTrack selected = playlist.getSelectedTrack();

                        if (selected != null) {
                            message.getChannel().sendMessage("Selected track from playlist: " + selected.getInfo().title).queue();
                        } else {
                            selected = tracks.get(0);
                            message.getChannel().sendMessage("Added first track from playlist: " + selected.getInfo().title).queue();
                        }

                        if (now) {
                            scheduler.playNow(selected, true);
                        } else {
                            scheduler.addToQueue(selected);
                        }

                        for (int i = 0; i < Math.min(10, playlist.getTracks().size()); i++) {
                            if (tracks.get(i) != selected) {
                                scheduler.addToQueue(tracks.get(i));
                            }
                        }
                    }


                    @Override
                    public void noMatches() {
                        message.getChannel().sendMessage("Nothing found for " + identifier).queue();
                    }

                    @Override
                    public void loadFailed(FriendlyException throwable) {
                        message.getChannel().sendMessage("Failed with message: " + throwable.getMessage() + " (" + throwable.getClass().getSimpleName() + ")").queue();
                    }
                });
            }
        }
    }

    private void forPlayingTrack(TrackOperation operation) {
        AudioTrack track = player.getPlayingTrack();

        if (track != null) {
            operation.execute(track);
        }
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            if (Bootstrap.getConfig().getVoiceChannelToUse(audioManager.getGuild()) != null) {
                audioManager.openAudioConnection(Bootstrap.getConfig().getVoiceChannelToUse(audioManager.getGuild()));
            } else {

                for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                    if ("Testing".equals(voiceChannel.getName())) {
                        audioManager.openAudioConnection(voiceChannel);
                        return;
                    }
                }


                for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                    audioManager.openAudioConnection(voiceChannel);
                    return;
                }
            }
        }
    }



    private interface TrackOperation {
        void execute(AudioTrack track);
    }

    private class GlobalDispatcher implements MessageDispatcher {
        @Override
        public void sendMessage(String message, Consumer<Message> success, Consumer<Throwable> failure) {
            TextChannel channel = outputChannel.get();

            if (channel != null) {
                channel.sendMessage(message).queue(success, failure);
            }
        }

        @Override
        public void sendMessage(String message) {
            TextChannel channel = outputChannel.get();

            if (channel != null) {
                channel.sendMessage(message).queue();
            }
        }
    }

    private final class FixedDispatcher implements MessageDispatcher {
        private final TextChannel channel;

        private FixedDispatcher(TextChannel channel) {
            this.channel = channel;
        }

        @Override
        public void sendMessage(String message, Consumer<Message> success, Consumer<Throwable> failure) {
            channel.sendMessage(message).queue(success, failure);
        }

        @Override
        public void sendMessage(String message) {
            channel.sendMessage(message).queue();
        }
    }

    public static class Factory implements BotControllerFactory<MusicController> {
        @Override
        public Class<MusicController> getControllerClass() {
            return MusicController.class;
        }

        @Override
        public MusicController create(BotApplicationManager manager, BotGuildContext state, Guild guild) {
            return new MusicController(manager, state, guild);
        }
    }
}
