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

    @BotCommandHandler(
            name = "add",
            usage = "add <identifier>"
    )
    private void add(Message message, String identifier) {
        addTrack(message, identifier, false);
    }

    @BotCommandHandler (
            name = "now",
            usage = "now <identifier>"
    )
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

    @BotCommandHandler (

    )
    private void eqsetup(Message message) {
        manager.getConfiguration().setFilterHotSwapEnabled(true);
        player.setFrameBufferDuration(500);
    }

    @BotCommandHandler (
            name = "eqstart",
            usage = "eqstart"
    )
    private void eqstart(Message message) {
        player.setFilterFactory(equalizer);
    }

    @BotCommandHandler (
            name = "eqstop",
            usage = "eqstop"
    )
    private void eqstop(Message message) {
        player.setFilterFactory(null);
    }

    @BotCommandHandler (
            name = "eqband",
            usage = "eqband <band> <value>"
    )
    private void eqband(Message message, int band, float value) {
        player.setFilterFactory(equalizer);
        equalizer.setGain(band, value);
    }

    @BotCommandHandler (
            name = "eqhighbass",
            usage = "eqhighbass <diff>"
    )
    private void eqhighbass(Message message, float diff) {
        player.setFilterFactory(equalizer);
        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, BASS_BOOST[i] + diff);
        }
    }

    @BotCommandHandler (
            name = "eqlowbass",
            usage = "eqlowbass <diff>"
    )
    private void eqlowbass(Message message, float diff) {
        player.setFilterFactory(equalizer);
        for (int i = 0; i < BASS_BOOST.length; i++) {
            equalizer.setGain(i, -BASS_BOOST[i] + diff);
        }
    }

    @BotCommandHandler (
            name = "bassboost",
            usage = "bassboost"
    )
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

    @BotCommandHandler (
            name = "volume",
            usage = "volume <0-100>"
    )
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

    @BotCommandHandler (
            name = "skip",
            usage = "skip"
    )
    private void skip(Message message) {
        scheduler.skip();
    }

    @BotCommandHandler (
            name = "forward",
            usage = "forward <seconds>"
    )
    private void forward(Message message, int duration) {
        forPlayingTrack(track -> track.setPosition(track.getPosition() + duration*1000));
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Forward " + duration + "seconds!")).queue();
    }

    @BotCommandHandler (
            name = "back",
            usage = "back <seconds>"
    )
    private void back(Message message, int duration) {
        forPlayingTrack(track -> track.setPosition(Math.max(0, track.getPosition() - duration*1000)));
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Backwards " + duration + "seconds!")).queue();
    }

    @BotCommandHandler (
            name = "pause",
            usage = "pause"
    )
    private void pause(Message message) {
        player.setPaused(true);
    }

    @BotCommandHandler (
            name = "resume",
            usage = "resume"
    )
    private void resume(Message message) {
        player.setPaused(false);
    }

    @BotCommandHandler (
            name = "duration",
            usage = "duration"
    )
    private void duration(Message message) {
        forPlayingTrack(track -> message.getChannel().sendMessage("Duration is " + track.getDuration()).queue());
    }

    @BotCommandHandler (
            name = "seek",
            usage = "seek <milliseconds>"
    )
    private void seek(Message message, long position) {
        forPlayingTrack(track -> track.setPosition(position));
        message.getChannel().sendMessage(MessageBuilder.buildSuccess("Jumped to " + position + "milliseconds!")).queue();
    }

    @BotCommandHandler (
            name = "pos",
            usage = "pos"
    )
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

    @BotCommandHandler (
            name = "version",
            usage = "version"
    )
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

    @BotCommandHandler (
            name = "leave",
            usage = "leave"
    )
    private void leave(Message message) {
        guild.getAudioManager().closeAudioConnection();
    }

    @BotCommandHandler(
            name = "stop",
            usage = "stop"
    )
    private void stop(Message message) {
        player.setPaused(true);
        guild.getAudioManager().closeAudioConnection();
    }

    @BotCommandHandler (
            name = "clear",
            usage = "clear"
    )
    private void clear(Message message) {
        scheduler.clearQueue();
        message.getChannel().sendMessage("Queue cleared!").queue();
    }

    @BotCommandHandler (
            name = "setvc",
            usage = "setvc <voice_channel_id>"
    )
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



    @BotCommandHandler (
            name = "listqueues",
            usage = "listqueues"
    )
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

    @BotCommandHandler (
            name = "savequeue",
            usage = "savequeue <name>"
    )
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

    @BotCommandHandler (
            name = "loadqueue",
            usage = "loadqueue <existing_name>"
    )
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


    @BotCommandHandler (
            name = "repeattrack",
            usage = "repeattrack"
    )
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

    @BotCommandHandler (
            name = "geturi",
            usage = "geturi"
    )
    private void geturi(Message message) {
        if (player.getPlayingTrack() == null) {
            message.getChannel().sendMessage(MessageBuilder.buildError("There is currently no track playing", null)).queue();
            return;
        }

        message.getChannel().sendMessage(player.getPlayingTrack().getInfo().uri).queue();
    }

    @BotCommandHandler (
            name = "geturl",
            usage = "geturl"
    )
    private void getUrl(Message message) {
        geturi(message);
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
