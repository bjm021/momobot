package de.bjm.momobot.sourceManager.youtube;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.youtube.*;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.http.ExtendedHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.http.MultiHttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * Audio source manager that implements finding Youtube videos or playlists based on an URL or ID.
 */
public class CustomYoutubeAudioSourceManager implements AudioSourceManager, HttpConfigurable {
  private static final Logger log = LoggerFactory.getLogger(CustomYoutubeAudioSourceManager.class);

  private final YoutubeSignatureResolver signatureResolver;
  private final HttpInterfaceManager httpInterfaceManager;
  private final ExtendedHttpConfigurable combinedHttpConfiguration;
  private final YoutubeMixLoader mixLoader;
  private final boolean allowSearch;
  private final YoutubeTrackDetailsLoader trackDetailsLoader;
  private final YoutubeSearchResultLoader searchResultLoader;
  private final CustomYoutubePlaylistLoader playlistLoader;
  private final YoutubeLinkRouter linkRouter;
  private final LoadingRoutes loadingRoutes;

  /**
   * Create an instance with default settings.
   */
  public CustomYoutubeAudioSourceManager() {
    this(true);
    System.out.println("[MomoBot2] Loading modified version of YoutubeSourceManager!");
  }

  /**
   * Create an instance.
   * @param allowSearch Whether to allow search queries as identifiers
   */
  public CustomYoutubeAudioSourceManager(boolean allowSearch) {
    this(
        allowSearch,
        new DefaultYoutubeTrackDetailsLoader(),
        new YoutubeSearchProvider(),
        new YoutubeSignatureCipherManager(),
        new CustomYoutubePlaylistLoader(),
        new DefaultYoutubeLinkRouter(),
        new YoutubeMixProvider()
    );
  }

  public CustomYoutubeAudioSourceManager(
      boolean allowSearch,
      YoutubeTrackDetailsLoader trackDetailsLoader,
      YoutubeSearchResultLoader searchResultLoader,
      YoutubeSignatureResolver signatureResolver,
      CustomYoutubePlaylistLoader playlistLoader,
      YoutubeLinkRouter linkRouter,
      YoutubeMixLoader mixLoader
  ) {
    httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    httpInterfaceManager.setHttpContextFilter(new YoutubeHttpContextFilter());

    this.allowSearch = allowSearch;
    this.trackDetailsLoader = trackDetailsLoader;
    this.signatureResolver = signatureResolver;
    this.searchResultLoader = searchResultLoader;
    this.playlistLoader = playlistLoader;
    this.linkRouter = linkRouter;
    this.mixLoader = mixLoader;
    this.loadingRoutes = new LoadingRoutes();

    combinedHttpConfiguration = new MultiHttpConfigurable(Arrays.asList(
        httpInterfaceManager,
        searchResultLoader.getHttpConfiguration()
    ));
  }

  public YoutubeTrackDetailsLoader getTrackDetailsLoader() {
    return trackDetailsLoader;
  }

  public YoutubeSignatureResolver getSignatureResolver() {
    return signatureResolver;
  }

  /**
   * @param playlistPageCount Maximum number of pages loaded from one playlist. There are 100 tracks per page.
   */
  public void setPlaylistPageCount(int playlistPageCount) {
    playlistLoader.setPlaylistPageCount(playlistPageCount);
  }

  @Override
  public String getSourceName() {
    return "youtube";
  }

  @Override
  public AudioItem loadItem(DefaultAudioPlayerManager manager, AudioReference reference) {
    try {
      return loadItemOnce(reference);
    } catch (FriendlyException exception) {
      // In case of a connection reset exception, try once more.
      if (HttpClientTools.isRetriableNetworkException(exception.getCause())) {
        return loadItemOnce(reference);
      } else {
        throw exception;
      }
    }
  }

  @Override
  public boolean isTrackEncodable(AudioTrack track) {
    return true;
  }

  @Override
  public void encodeTrack(AudioTrack track, DataOutput output) {
    // No custom values that need saving
  }

  @Override
  public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
    return new CustomYoutubeAudioTrack(trackInfo, this);
  }

  @Override
  public void shutdown() {
    ExceptionTools.closeWithWarnings(httpInterfaceManager);
  }

  /**
   * @return Get an HTTP interface for a playing track.
   */
  public HttpInterface getHttpInterface() {
    return httpInterfaceManager.getInterface();
  }

  @Override
  public void configureRequests(Function<RequestConfig, RequestConfig> configurator) {
    combinedHttpConfiguration.configureRequests(configurator);
  }

  @Override
  public void configureBuilder(Consumer<HttpClientBuilder> configurator) {
    combinedHttpConfiguration.configureBuilder(configurator);
  }

  public ExtendedHttpConfigurable getHttpConfiguration() {
    return combinedHttpConfiguration;
  }

  public ExtendedHttpConfigurable getMainHttpConfiguration() {
    return httpInterfaceManager;
  }

  public ExtendedHttpConfigurable getSearchHttpConfiguration() {
    return searchResultLoader.getHttpConfiguration();
  }

  private AudioItem loadItemOnce(AudioReference reference) {
    return linkRouter.route(reference.identifier, loadingRoutes);
  }

  /**
   * Loads a single track from video ID.
   *
   * @param videoId ID of the YouTube video.
   * @param mustExist True if it should throw an exception on missing track, otherwise returns AudioReference.NO_TRACK.
   * @return Loaded YouTube track.
   */
  public AudioItem loadTrackWithVideoId(String videoId, boolean mustExist) {
    try (HttpInterface httpInterface = getHttpInterface()) {
      YoutubeTrackDetails details = trackDetailsLoader.loadDetails(httpInterface, videoId, false);

      if (details == null) {
        if (mustExist) {
          throw new FriendlyException("Video unavailable", COMMON, null);
        } else {
          return AudioReference.NO_TRACK;
        }
      }

      return new CustomYoutubeAudioTrack(details.getTrackInfo(), this);
    } catch (Exception e) {
      throw ExceptionTools.wrapUnfriendlyExceptions("Loading information for a YouTube track failed.", FAULT, e);
    }
  }

  private CustomYoutubeAudioTrack buildTrackFromInfo(AudioTrackInfo info) {
    return new CustomYoutubeAudioTrack(info, this);
  }

  private class LoadingRoutes implements YoutubeLinkRouter.Routes<AudioItem> {

    @Override
    public AudioItem track(String videoId) {
      return loadTrackWithVideoId(videoId, false);
    }

    @Override
    public AudioItem playlist(String playlistId, String selectedVideoId) {
      System.out.println("Starting to load playlist with ID {}" + playlistId);

      try (HttpInterface httpInterface = getHttpInterface()) {
        AudioPlaylist list = playlistLoader.load(httpInterface, playlistId, selectedVideoId,
            CustomYoutubeAudioSourceManager.this::buildTrackFromInfo);
        System.out.println("The Loaded playlist should have " + list.getTracks().size() + " Tracks!");
        return list;
      } catch (Exception e) {
        System.err.println("[MomoBot2] [CustomYASM] Error occurred loading: ");
        e.printStackTrace();
        throw ExceptionTools.wrapUnfriendlyExceptions(e);
      }
    }

    @Override
    public AudioItem mix(String mixId, String selectedVideoId) {
      System.out.println("Starting to load mix with ID {} selected track {}" + mixId + selectedVideoId);

      try (HttpInterface httpInterface = getHttpInterface()) {
        return mixLoader.load(httpInterface, mixId, selectedVideoId,
            CustomYoutubeAudioSourceManager.this::buildTrackFromInfo);
      } catch (Exception e) {
        throw ExceptionTools.wrapUnfriendlyExceptions(e);
      }
    }

    @Override
    public AudioItem search(String query) {
      if (allowSearch) {
        return searchResultLoader.loadSearchResult(
            query,
            CustomYoutubeAudioSourceManager.this::buildTrackFromInfo
        );
      } else {
        return null;
      }
    }

    @Override
    public AudioItem anonymous(String videoIds) {
      try (HttpInterface httpInterface = getHttpInterface()) {
        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/watch_videos?video_ids=" + videoIds))) {
          int statusCode = response.getStatusLine().getStatusCode();
          HttpClientContext context = httpInterface.getContext();
          if (!HttpClientTools.isSuccessWithContent(statusCode)) {
            throw new IOException("Invalid status code for playlist response: " + statusCode);
          }
          // youtube currently transforms watch_video links into a link with a video id and a list id.
          // because thats what happens, we can simply re-process with the redirected link
          List<URI> redirects = context.getRedirectLocations();
          if (redirects != null && !redirects.isEmpty()) {
            return new AudioReference(redirects.get(0).toString(), null);
          } else {
            throw new FriendlyException("Unable to process youtube watch_videos link", SUSPICIOUS,
                new IllegalStateException("Expected youtube to redirect watch_videos link to a watch?v={id}&list={list_id} link, but it did not redirect at all"));
          }
        }
      } catch (Exception e) {
        throw ExceptionTools.wrapUnfriendlyExceptions(e);
      }
    }

    @Override
    public AudioItem none() {
      return AudioReference.NO_TRACK;
    }
  }
}
