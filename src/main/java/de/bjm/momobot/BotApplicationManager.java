package de.bjm.momobot;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager;
import com.sedmelluq.lava.common.tools.DaemonThreadFactory;
import de.bjm.momobot.controller.BotCommandMappingHandler;
import de.bjm.momobot.controller.BotController;
import de.bjm.momobot.controller.BotControllerManager;
import de.bjm.momobot.file.Config;
import de.bjm.momobot.music.MusicController;
import de.bjm.momobot.utils.Hentai;
import de.bjm.momobot.utils.MessageBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
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
 */
public class BotApplicationManager extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BotApplicationManager.class);

    private final Map<Long, BotGuildContext> guildContexts;
    private final BotControllerManager controllerManager;
    private final AudioPlayerManager playerManager;
    private final ScheduledExecutorService executorService;


    public BotApplicationManager() {

        guildContexts = new HashMap<>();
        controllerManager = new BotControllerManager();

        controllerManager.registerController(new MusicController.Factory());
        controllerManager.registerController(new Commands.Factory());

        playerManager = new DefaultAudioPlayerManager();
        //playerManager.useRemoteNodes("localhost:8080");
        playerManager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.LOW);
        playerManager.registerSourceManager(new YoutubeAudioSourceManager());
        playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        playerManager.registerSourceManager(new BandcampAudioSourceManager());
        playerManager.registerSourceManager(new VimeoAudioSourceManager());
        playerManager.registerSourceManager(new TwitchStreamAudioSourceManager());
        playerManager.registerSourceManager(new BeamAudioSourceManager());
        playerManager.registerSourceManager(new HttpAudioSourceManager());
        playerManager.registerSourceManager(new LocalAudioSourceManager());


        executorService = Executors.newScheduledThreadPool(1, new DaemonThreadFactory("bot"));
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }

    private BotGuildContext createGuildState(long guildId, Guild guild) {
        BotGuildContext context = new BotGuildContext(guildId);

        for (BotController controller : controllerManager.createControllers(this, context, guild)) {
            context.controllers.put(controller.getClass(), controller);
        }

        return context;
    }

    private synchronized BotGuildContext getContext(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        BotGuildContext context = guildContexts.get(guildId);

        if (context == null) {
            context = createGuildState(guildId, guild);
            guildContexts.put(guildId, context);
        }

        return context;
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        Member member = event.getMember();

        if (!event.isFromType(ChannelType.TEXT) || member == null || member.getUser().isBot()) {
            return;
        }

        Bootstrap.getTwoLogic().handleEvent(event);

        BotGuildContext guildContext = getContext(event.getGuild());


        if ((event.getMessage().getContentRaw().startsWith("-hentai") ||
                event.getMessage().getContentRaw().startsWith("-rule34") ||
                event.getMessage().getContentRaw().startsWith("-real") ||
                event.getMessage().getContentRaw().startsWith("-safe")) &&
                event.getMessage().getContentRaw().split(" ").length == 2) {

            if (event.getMessage().getContentRaw().startsWith("-rule34") || event.getMessage().getContentRaw().startsWith("-hentai")) {
                try {
                    int c = Integer.parseInt(event.getMessage().getContentRaw().split(" ")[1]);
                    Hentai.hentai(Hentai.sites.RULE_34, new ArrayList<>(), c, event.getChannel());
                } catch (NumberFormatException e) {
                    event.getTextChannel().sendMessage("The first argument is not a number!").queue();
                }
            } else if (event.getMessage().getContentRaw().startsWith("-safe")) {
                try {
                    int c = Integer.parseInt(event.getMessage().getContentRaw().split(" ")[1]);
                    Hentai.hentai(Hentai.sites.SAFEBOORU, new ArrayList<>(), c, event.getChannel());
                } catch (NumberFormatException e) {
                    event.getTextChannel().sendMessage("The first argument is not a number!").queue();
                }
            } else if (event.getMessage().getContentRaw().startsWith("-real")) {
                try {
                    int c = Integer.parseInt(event.getMessage().getContentRaw().split(" ")[1]);
                    Hentai.hentai(Hentai.sites.REALBOORU, new ArrayList<>(), c, event.getChannel());
                } catch (NumberFormatException e) {
                    event.getTextChannel().sendMessage("The first argument is not a number!").queue();
                }
            }

            return;
        }

        String prefix = Bootstrap.getConfig().getValue(Config.ConfigValue.PREFIX);
        if (prefix == null)
            prefix = "-";
        controllerManager.dispatchMessage(guildContext.controllers, prefix, event.getMessage(), new BotCommandMappingHandler() {
            @Override
            public void commandNotFound(Message message, String name) {
                event.getTextChannel().sendMessage(MessageBuilder.buildError("This command does not exist", null)).queue();
            }

            @Override
            public void commandWrongParameterCount(Message message, String name, String usage, int given, int required) {
                event.getTextChannel().sendMessage(MessageBuilder.buildError("Wrong argument count for command. Usage: " + Bootstrap.getConfig().getValue(Config.ConfigValue.PREFIX) + usage + " ", null)).queue();
            }

            @Override
            public void commandWrongParameterType(Message message, String name, String usage, int index, String value, Class<?> expectedType) {
                event.getTextChannel().sendMessage(MessageBuilder.buildError("Wrong argument type for command (See -help for usage)", null)).queue();
            }

            @Override
            public void commandRestricted(Message message, String name) {
                event.getTextChannel().sendMessage(MessageBuilder.buildError("Command not permitted", null)).queue();
            }

            @Override
            public void commandException(Message message, String name, Throwable throwable) {
                event.getTextChannel().sendMessage(MessageBuilder.buildError("Command threw an exception", new Exception(throwable))).queue();

                log.error("Command with content {} threw an exception.", message.getContentDisplay(), throwable);
            }
        });
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        // do stuff
    }
}
