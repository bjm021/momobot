package de.bjm.momobot.sourceManager.youtube;

import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubePlaylistLoader;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.Units;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import de.bjm.momobot.Bootstrap;
import de.bjm.momobot.file.Config;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public class CustomYoutubePlaylistLoader implements YoutubePlaylistLoader {
  private volatile int playlistPageCount = 10;

  public CustomYoutubePlaylistLoader() {
    System.out.println("[MomoBot2] [CustomYPL] Loading customYPL.");
  }

  @Override
  public void setPlaylistPageCount(int playlistPageCount) {
    this.playlistPageCount = playlistPageCount;
  }

  @Override
  public AudioPlaylist load(HttpInterface httpInterface, String playlistId, String selectedVideoId,
                            Function<AudioTrackInfo, AudioTrack> trackFactory) {

    HttpGet request = new HttpGet(getPlaylistUrl(playlistId) + "&pbj=1&hl=en");

    try (CloseableHttpResponse response = httpInterface.execute(request)) {
      int statusCode = response.getStatusLine().getStatusCode();
      if (!HttpClientTools.isSuccessWithContent(statusCode)) {
        throw new IOException("Invalid status code for playlist response: " + statusCode);
      }

      JsonBrowser json = JsonBrowser.parse(response.getEntity().getContent());

      return buildPlaylist(httpInterface, json, selectedVideoId, trackFactory);
    } catch (IOException e) {
      System.err.println("[MomoBot2] [CustomYASM] Error occurred loading: ");
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private AudioPlaylist buildPlaylist(HttpInterface httpInterface, JsonBrowser json, String selectedVideoId,
                                      Function<AudioTrackInfo, AudioTrack> trackFactory) throws IOException {

    JsonBrowser jsonResponse = json.index(1).get("response");

    JsonBrowser alerts = jsonResponse.get("alerts");

    if (Bootstrap.getConfig().getValue(Config.ConfigValue.ENABLE_UNSAFE_PLAYLISTS).equalsIgnoreCase("false")) {
      if (!alerts.isNull()) {
        System.err.println("[MomoBot2] [CustomYASM] Error occurred loading: ");
        System.out.println(json.text());
        System.err.println(alerts.index(0).get("alertRenderer").get("text").get("simpleText").text());
        throw new FriendlyException(alerts.index(0).get("alertRenderer").get("text").get("simpleText").text(), COMMON, null);
      }
    }



    JsonBrowser info = jsonResponse
        .get("sidebar")
        .get("playlistSidebarRenderer")
        .get("items")
        .index(0)
        .get("playlistSidebarPrimaryInfoRenderer");

    String playlistName = info
        .get("title")
        .get("runs")
        .index(0)
        .get("text")
        .text();

    JsonBrowser playlistVideoList = jsonResponse
        .get("contents")
        .get("twoColumnBrowseResultsRenderer")
        .get("tabs")
        .index(0)
        .get("tabRenderer")
        .get("content")
        .get("sectionListRenderer")
        .get("contents")
        .index(0)
        .get("itemSectionRenderer")
        .get("contents")
        .index(0)
        .get("playlistVideoListRenderer")
        .get("contents");

    List<AudioTrack> tracks = new ArrayList<>();
    String loadMoreUrl = extractPlaylistTracks(playlistVideoList, tracks, trackFactory);
    int loadCount = 0;
    int pageCount = playlistPageCount;

    // Also load the next pages, each result gives us a JSON with separate values for list html and next page loader html
    while (loadMoreUrl != null && ++loadCount < pageCount) {
      try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com" + loadMoreUrl))) {
        int statusCode = response.getStatusLine().getStatusCode();
        if (!HttpClientTools.isSuccessWithContent(statusCode)) {
          System.err.println("[MomoBot2] [CustomYASM] Error occurred loading: ");
          System.err.println("At: " + "https://www.youtube.com" + loadMoreUrl);
          System.err.println("Invalid status code for playlist response: " + statusCode);
          throw new IOException("Invalid status code for playlist response: " + statusCode);
        }

        JsonBrowser continuationJson = JsonBrowser.parse(response.getEntity().getContent());

        JsonBrowser playlistVideoListPage = continuationJson.index(1)
            .get("response")
            .get("continuationContents")
            .get("playlistVideoListContinuation");

        if (playlistVideoListPage.isNull()) {
          playlistVideoListPage = continuationJson.index(1)
              .get("response")
              .get("onResponseReceivedActions")
              .index(0)
              .get("appendContinuationItemsAction")
              .get("continuationItems");
        }

        loadMoreUrl = extractPlaylistTracks(playlistVideoListPage, tracks, trackFactory);
      } catch (Exception e) {
        System.err.println("[MomoBot2] [CustomYASM] Error occurred loading: ");
        e.printStackTrace();
      }
    }

    return new BasicAudioPlaylist(playlistName, tracks, findSelectedTrack(tracks, selectedVideoId), false);
  }

  private AudioTrack findSelectedTrack(List<AudioTrack> tracks, String selectedVideoId) {
    if (selectedVideoId != null) {
      for (AudioTrack track : tracks) {
        if (selectedVideoId.equals(track.getIdentifier())) {
          return track;
        }
      }
    }

    return null;
  }

  private String extractPlaylistTracks(JsonBrowser playlistVideoList, List<AudioTrack> tracks,
                                       Function<AudioTrackInfo, AudioTrack> trackFactory) {

    if (playlistVideoList.isNull()) return null;

    final List<JsonBrowser> playlistTrackEntries = playlistVideoList.values();
    for (JsonBrowser track : playlistTrackEntries) {
      JsonBrowser item = track.get("playlistVideoRenderer");

      JsonBrowser shortBylineText = item.get("shortBylineText");

      // If the isPlayable property does not exist, it means the video is removed or private
      // If the shortBylineText property does not exist, it means the Track is Region blocked
      if (!item.get("isPlayable").isNull() && !shortBylineText.isNull()) {
        String videoId = item.get("videoId").text();
        JsonBrowser titleField = item.get("title");
        String title = Optional.ofNullable(titleField.get("simpleText").text())
                .orElse(titleField.get("runs").index(0).get("text").text());
        String author = shortBylineText.get("runs").index(0).get("text").text();
        JsonBrowser lengthSeconds = item.get("lengthSeconds");
        long duration = Units.secondsToMillis(lengthSeconds.asLong(Units.DURATION_SEC_UNKNOWN));

        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false,
            "https://www.youtube.com/watch?v=" + videoId);

        tracks.add(trackFactory.apply(info));
      }
    }

    JsonBrowser continuations = playlistVideoList.get("continuations");

    String continuationsToken;
    if (!continuations.isNull()) {
      continuationsToken = continuations.index(0).get("nextContinuationData").get("continuation").text();
    } else {
      continuations = playlistTrackEntries
          .get(playlistTrackEntries.size() -1)
          .get("continuationItemRenderer");
      continuationsToken = continuations.get("continuationEndpoint").get("continuationCommand").get("token").text();
    }

    if (continuationsToken != null && !continuationsToken.isEmpty()) {
      return "/browse_ajax?continuation=" + continuationsToken + "&ctoken=" + continuationsToken + "&hl=en";
    }

    return null;
  }

  private static String getPlaylistUrl(String playlistId) {
    return "https://www.youtube.com/playlist?list=" + playlistId;
  }
}
