package de.bjm.momobot.utils;

import net.dv8tion.jda.api.entities.MessageChannel;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * This is an implementation of the Hentai API coded by b.jm021.
 * This API uses the sites https://rule34.xxx and https://safebooru.org/ and https://realbooru.com
 *
 * It works in the following way:
 *  The API takes a limit and a list of tags
 *  Per API request there are 100 posts in an XML response
 *  If there are more than 600 Posts there will only be
 *  6 requests per site so that you will not be banned!
 *  It chooses a page id randomly and makes 6 different API requests
 *  Then it will divide the limit by 2 and takes limit/2 images from both sides
 */
@SuppressWarnings("DuplicatedCode")
public class Hentai {



    public enum sites {
        RULE_34, REALBOORU, SAFEBOORU
    }


    /**
     * The API implementation!
     * Api usage explained above
     * @param site      The booru site to pull from
     * @param tags      An {@link String} Array containing the different Tags
     * @param limit     Tha amount of images to send
     * @param channel   The Discord {@link MessageChannel} in which to post the images
     */
    public static void hentai(sites site, List<String> tags, int limit, MessageChannel channel) {
        channel.sendMessage(MessageBuilder.buildSuccess("Trying to send " + limit + " images with tags: " + String.join(", ", tags))).queue();
        CloseableHttpClient client= HttpClientBuilder.create().build();
        StringBuilder sb = new StringBuilder();

        switch (site) {
            case RULE_34:
                sb.append("https://rule34.xxx/");
                break;
            case REALBOORU:
                sb.append("https://realbooru.com/");
                break;
            case SAFEBOORU:
                sb.append("https://safebooru.org/");
                break;
        }

        sb.append("index.php?page=dapi&s=post&q=index&tags=");
        for (String tag : tags) {
            sb.append(tag);
            sb.append("%20");
        }
        System.out.println(sb.toString());
        HttpGet get = new HttpGet(sb.toString());
        get.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
        try {
            CloseableHttpResponse response = client.execute(get);
            SAXBuilder builder = new SAXBuilder();
            Document firstDoc = builder.build(response.getEntity().getContent());
            Element rootElement = firstDoc.getRootElement();

            int postCount = Integer.parseInt(rootElement.getAttributeValue("count"));
            int pageCount = postCount/100;

            List<Document> docList = new ArrayList<>();
            if(pageCount <= 6) {
                for (int i = 0; i <= 6; i++) {
                    StringBuilder tmpBuilder = new StringBuilder();
                    tmpBuilder.append(sb.toString());
                    tmpBuilder.append("&pid=");
                    tmpBuilder.append(i);
                    System.out.println(tmpBuilder.toString());
                    CloseableHttpClient tmpClient = HttpClientBuilder.create().build();
                    HttpGet tmpGet = new HttpGet(tmpBuilder.toString());
                    get.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
                    CloseableHttpResponse tmpResponse = tmpClient.execute(tmpGet);
                    Document tmpDoc = builder.build(tmpResponse.getEntity().getContent());
                    docList.add(tmpDoc);
                    tmpResponse.close();
                    tmpClient.close();
                }
                System.out.println(docList.size());
            } else {
                for (int i = 0; i <= 6; i++) {
                    StringBuilder tmpBuilder = new StringBuilder();
                    tmpBuilder.append(sb.toString());
                    tmpBuilder.append("&pid=");
                    tmpBuilder.append(ThreadLocalRandom.current().nextInt(1, pageCount+1));
                    System.out.println(tmpBuilder.toString());
                    CloseableHttpClient tmpClient = HttpClientBuilder.create().build();
                    HttpGet tmpGet = new HttpGet(tmpBuilder.toString());
                    get.setHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 6.1; en-US; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)");
                    CloseableHttpResponse tmpResponse = tmpClient.execute(tmpGet);
                    Document tmpDoc = builder.build(tmpResponse.getEntity().getContent());
                    docList.add(tmpDoc);
                    tmpResponse.close();
                    tmpClient.close();
                }
            }

            List<Element> postList = new ArrayList<>();
            //docList.forEach(document -> {
            //    System.out.println(document.getRootElement());
            //});
            for (Document doc:docList) {

                //System.out.println("[DEBUG!!!!!!]");
                //doc.getRootElement().getChildren().forEach(System.out::println);

                postList.addAll(doc.getRootElement().getChildren("post"));
            }

            if (postList.size() == 0) {
                channel.sendMessage(MessageBuilder.buildError("No images found by the given tags", null)).queue();
            }

            for (int i = 0; i < limit; i++) {
                int random = ThreadLocalRandom.current().nextInt(0, postList.size());
                System.out.print("random: " + random + " ");
                Element el = postList.get(random);
                String output = "";
                if(el.getAttributeValue("file_url").startsWith("/")) {
                    output = "https:";
                }
                output = output + el.getAttributeValue("file_url");
                channel.sendMessage(output).queue();
            }

            response.close();
            client.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
