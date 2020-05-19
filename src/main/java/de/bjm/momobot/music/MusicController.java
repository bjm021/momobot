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
    private void help(Message message) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("HELP");
        eb.setColor(Color.BLUE);
        eb.setDescription("All Bot Commands, args in <arg> required | args in [arg] optional");
        eb.addField("", "Generic COMMANDS", false);
        eb.addField("-now <url>", "Plays a track instantly", true);
        eb.addField("-add <url>", "Adds a track to Queue", true);
        eb.addField("-pause", "Pauses the player", true);
        eb.addField("-resume", "Resumes after pause", true);
        eb.addField("-volume <value>", "Sets the audio volume", true);
        eb.addField("-clear", "Clears the queue", true);
        eb.addField("", "EQ COMMANDS", false);
        eb.addField("-eqstart", "Engages the Equalizer (auto when using eq commands)", true);
        eb.addField("-eqstop", "Disengages the Equalizer", true);
        eb.addField("-eqband <band> <value>", "Modify the EQ", true);
        eb.addField("-eqhighbass <value>", "Modify the high band bass", true);
        eb.addField("-bassboost", "BASSBOOST (EARRAPE!!!)", true);
        eb.addField("-eqlowbass <value>", "Modify the low band bass", true);
        eb.addField("", "NSFW Commands", false);
        eb.addField("-rule34 / -hentai <amount> [tags]", "Pulls Hentai images from Rule34", true);
        eb.addField("-real <amount> [tags]", "Pulls Real images from Realbooru", true);
        eb.addField("-safe <amount> [tags]", "Pulls SFW images from Safebooru", true);
        eb.addField("", "Bot Settings Commands", false);
        eb.addField("-setvc <channel_id>", "Sets the VoiceChannel the bot uses", true);
        eb.addField("-setdebug <true/false>", "Sets the DEBUG mode of the bot", true);
        eb.setAuthor("MomoBot " + Bootstrap.VERSION, "https://momobot.cf", "https://cdn.discordapp.com/avatars/687607623650246677/b3676d9410b5af9a4527f216265b7441.png");
        eb.setThumbnail("http://cdn.bjm.hesteig.com/BJM_Logo_white.png");
        message.getChannel().sendMessage(eb.build()).queue();

        eb = new EmbedBuilder();
        eb.setColor(Color.BLUE);
        eb.addField("", "Queue Management", false);
        eb.addField("-savequeue <name>", "Saves the last 8 queue items in a file", true);
        eb.addField("-listqueues", "List saved queues", true);
        eb.addField("-loadqueue <name>", "Loads all track of a saved queue by name", true);
        eb.addField("", "Administrative commands", false);
        eb.addField("-addadmin <id>", "Promote a user to admin", true);
        eb.addField("-removeadmin <id>", "Demote a user from admin", true);
        eb.addField("-listadmins", "List all admins", true);
        eb.addField("Hint", "To restrict commands add their names (without the prefix) to the config.json file! After that only admins have permission to execute these commands!", false);
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
