package de.bjm.momobot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import de.bjm.momobot.MessageDispatcher;
import net.dv8tion.jda.api.entities.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
public class MusicScheduler extends AudioEventAdapter implements Runnable {
  private final AudioPlayer player;
  private final MessageDispatcher messageDispatcher;
  private final ScheduledExecutorService executorService;
  private final BlockingDeque<AudioTrack> queue;
  private final AtomicReference<Message> boxMessage;
  private final AtomicBoolean creatingBoxMessage;

  public BlockingDeque<AudioTrack> getQueue() {
    return queue;
  }

  public MusicScheduler(AudioPlayer player, MessageDispatcher messageDispatcher, ScheduledExecutorService executorService) {
    this.player = player;
    this.messageDispatcher = messageDispatcher;
    this.executorService = executorService;
    this.queue = new LinkedBlockingDeque<>();
    this.boxMessage = new AtomicReference<>();
    this.creatingBoxMessage = new AtomicBoolean();

    executorService.scheduleAtFixedRate(this, 3000L, 15000L, TimeUnit.MILLISECONDS);
  }

  public void addToQueue(AudioTrack audioTrack) {
    queue.addLast(audioTrack);
    startNextTrack(true);
  }

  public void clearQueue() {
    queue.clear();
  }

  public List<AudioTrack> drainQueue() {
    List<AudioTrack> drainedQueue = new ArrayList<>();
    queue.drainTo(drainedQueue);
    return drainedQueue;
  }

  public void playNow(AudioTrack audioTrack, boolean clearQueue) {
    if (clearQueue) {
      queue.clear();
    }

    queue.addFirst(audioTrack);
    startNextTrack(false);
  }

  public void skip() {
    startNextTrack(false);
  }

  private void startNextTrack(boolean noInterrupt) {
    AudioTrack next = queue.pollFirst();

    if (next != null) {
      if (!player.startTrack(next, noInterrupt)) {
        queue.addFirst(next);
      }
    } else {
      player.stopTrack();

      messageDispatcher.sendMessage("Queue finished.");
    }
  }

  @Override
  public void onTrackStart(AudioPlayer player, AudioTrack track) {
    updateTrackBox(true);
  }

  @Override
  public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
    if (endReason.mayStartNext) {
      if (MusicController.repeat) {
        playNow(track, false);
        messageDispatcher.sendMessage("Repeating track: " + track.getInfo().title);
        return;
      }
      startNextTrack(true);
      messageDispatcher.sendMessage(String.format("Track %s finished.", track.getInfo().title));
    }
  }

  @Override
  public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
    messageDispatcher.sendMessage(String.format("Track %s got stuck, skipping.", track.getInfo().title));

    startNextTrack(false);
  }

  @Override
  public void onPlayerResume(AudioPlayer player) {
    updateTrackBox(false);
  }

  @Override
  public void onPlayerPause(AudioPlayer player) {
    updateTrackBox(false);
  }

  private void updateTrackBox(boolean newMessage) {
    AudioTrack track = player.getPlayingTrack();

    if (track == null || newMessage) {
      Message message = boxMessage.getAndSet(null);

      if (message != null) {
        message.delete();
      }
    }

    if (track != null) {
      Message message = boxMessage.get();
      String box = TrackBoxBuilder.buildTrackBox(80, track, player.isPaused(), player.getVolume());

      if (message != null) {
        message.editMessage(box).queue();
      } else {
        if (creatingBoxMessage.compareAndSet(false, true)) {
          messageDispatcher.sendMessage(box, created -> {
            boxMessage.set(created);
            creatingBoxMessage.set(false);
          }, error -> {
            creatingBoxMessage.set(false);
          });
        }
      }
    }
  }

  @Override
  public void run() {
    updateTrackBox(false);
  }
}
