package demo;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Two goals: glom the decks from <A href="http://speakderdeck.com">SpeakerDeck</A>,
 * then upload to <a href="http://slideshare.net">Slideshare</a>.
 *
 * @author Josh Long (josh@joshlong.com)
 */
@Component
class PresentationSynchronizer implements InitializingBean {

    private final Logger logger = Logger.getLogger(Main.class);

    private volatile boolean running = true;

    private void enumerate(Iterator<SpeakerDeckPresentation> iterator) {
        while (iterator.hasNext()) {
            SpeakerDeckPresentation presentation = iterator.next();
            Assert.notNull(presentation);
            logger.debug("found presentation:" + presentation.toString());
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        SpeakerDeckClient speakerDeckClient = new SpeakerDeckClient();

        // test searching results
        Iterator<SpeakerDeckPresentation> results =
                speakerDeckClient.searchResultPresentations("Spring Framework");
        this.enumerate(results);
    }
}

@Configuration
@ComponentScan
public class Main {
    public static void main(String[] args) throws Throwable {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(Main.class);
        context.registerShutdownHook();
        context.start();
    }
}


/**
 * Client to return information on search results from a given Speakerdeck page.
 * <p/>
 * Possible values include account profiles like and search
 * results like .
 */
@Component
class SpeakerDeckClient {

    private final String speakerDeckBaseUri = "https://speakerdeck.com";

    private RestTemplate restTemplate;

    public SpeakerDeckClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public SpeakerDeckClient() {
        this(new RestTemplate());
    }


    private Iterator<SpeakerDeckPresentation> userPresentations(String user) {
        return null;
    }

    public Iterator<SpeakerDeckPresentation> searchResultPresentations(String query) {

        URI uri = UriComponentsBuilder
                .fromHttpUrl(speakerDeckBaseUri + "/search")
                .queryParam("q", query)
                .build()
                .toUri();

        try {
            return this.crawl(uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private String get(URI uri) {
        ResponseEntity<String> responseEntity = restTemplate.exchange(
            uri, HttpMethod.GET, null, new ParameterizedTypeReference<String>() {});
        return responseEntity.getBody();
    }


    // todo remove this. This is a mock implementation of #get so that I can avoid deluging the website itself during development.
   /* private String get(URI uri) {
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        File fakeHtml = new File(desktop, "sample.html");
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(fakeHtml));
            String line = null, in = "";
            while ((line = bufferedReader.readLine()) != null) {
                in = in + line;
            }
            return in;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
*/

    /**
     * Reads the results from a given page and gives the client back a (delegating) iterator.
     *
     * @see {@code http://www.w3.org/TR/2009/PR-css3-selectors-20091215/#attribute-representation}
     */
    private Iterator<SpeakerDeckPresentation> crawl(URI uri) throws Exception {
        String htmlForPage = get(uri);

        List<SpeakerDeckPresentation> presentations = new ArrayList<SpeakerDeckPresentation>();

        Document document = Jsoup.parse(htmlForPage, this.speakerDeckBaseUri);
        Elements publicTalks = document.select("[class=talk public]");
        for (org.jsoup.nodes.Element e : publicTalks) {

            // account information
            SpeakerDeckAccount account = null;
            SpeakerDeckPresentation presentation = null;

            for (org.jsoup.nodes.Element dateE : e.select("[class=date]")) {
                Elements aElement = dateE.select("a[href]");
                String humanName = aElement.text();
                String profileHref = aElement.first().absUrl("href");
                account = new SpeakerDeckAccount(URI.create(profileHref), humanName);
            }

            // title
            for (org.jsoup.nodes.Element titleE :
                    e.select("[class=talk-listing-meta]").select("h3[class=title]")) {
                Element aElement = titleE.select("a").first();
                String title = aElement.getAllElements().text();
                String url = aElement.absUrl("href");
                String dataId = e.attr("data-id");
                String dataSlideCount = e.attr("data-slide-count");
                presentation = new SpeakerDeckPresentation(account, URI.create(url), dataId, title, Integer.parseInt(dataSlideCount));
            }
            presentations.add(presentation);
        }
        return new SpeakerDeckIterator(presentations);
    }

    private class SpeakerDeckIterator implements Iterator<SpeakerDeckPresentation> {

        private List<SpeakerDeckPresentation> presentations;
        private Iterator<SpeakerDeckPresentation> presentationsIterator;

        public SpeakerDeckIterator(List<SpeakerDeckPresentation> presentations) {
            this.presentations = presentations;
            this.presentationsIterator = this.presentations.iterator();
        }

        @Override
        public boolean hasNext() {
            return this.presentationsIterator.hasNext();
        }

        @Override
        public SpeakerDeckPresentation next() {
            return this.presentationsIterator.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException(
                    "#remove() is not supported");
        }
    }
}

class SpeakerDeckAccount {

    private String name;
    private URI uri;

    @Override
    public boolean equals(Object o) {
        assert o instanceof SpeakerDeckAccount;
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public URI getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SIMPLE_STYLE);
    }

    public SpeakerDeckAccount(URI uri, String name) {
        this.uri = uri;
        this.name = name;
    }
}

class SpeakerDeckPresentation implements Serializable {

    private SpeakerDeckAccount account;
    private URI uri;
    private String dataId;
    private String title;
    private int dataSlideCount;

    public SpeakerDeckPresentation(SpeakerDeckAccount account, URI uri, String dataId, String title, int dataSlideCount) {
        this.account = account;
        this.uri = uri;
        this.dataId = dataId;
        this.title = title;
        this.dataSlideCount = dataSlideCount;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

    @Override
    public boolean equals(Object o) {
        assert o instanceof SpeakerDeckPresentation;
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}