package demo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
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
        Iterator<SpeakerDeckPresentation> results = speakerDeckClient.searchResultPresentations("Spring Framework");
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


@Component
class SpeakerDeckClient {

    private final String speakerDeckBaseUri = "https://speakerdeck.com";
    private Logger logger = Logger.getLogger(getClass());
    private boolean debug = true;
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
        return new SpeakerDeckIterator(uri);
    }

    private String fileNameForURI(URI uri) {
        String uriStr = uri.toString();
        String nString = "";
        for (char c : uriStr.toCharArray()) {
            nString += Character.isLetterOrDigit(c) ? c : "";
        }
        return nString + ".html";
    }

    private String get(URI uri) {
        try {
            String content = null;

            if (this.debug) {
                logger.debug("get('" + uri.toString() + "')");
                File tmpDir = SystemUtils.getJavaIoTmpDir();
                File fileForUri = new File(tmpDir, fileNameForURI(uri));
                logger.debug("checking for file '" + fileForUri.toString() + "'");
                if (fileForUri.exists()) {

                    return FileUtils.readFileToString(fileForUri);

                } else {
                    content = doGet(uri);
                    try (FileWriter fw = new FileWriter(fileForUri)) {
                        IOUtils.write(content, fw);
                        logger.debug("writing to file '" + fileForUri.toString() + "'.");
                    }

                }
            }

            if (content == null) { // it may not be if debug == true
                content = doGet(uri);
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String doGet(URI uri) {
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                uri, HttpMethod.GET, null, new ParameterizedTypeReference<String>() {
        });
        return responseEntity.getBody();
    }


    private static class CrawlResults {
        private URI next;
        private List<SpeakerDeckPresentation> presentations = new ArrayList<>();
        private int currentPage;

        @Override
        public String toString() {
            ToStringBuilder toStringBuilder
                    = new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                    .append("currentPageNo", currentPage)
                    .append("presentations", presentations.size())
                    .append("next", this.next == null ? "" : this.next.toString());
            return toStringBuilder.toString();
        }

        public URI getNext() {
            return next;
        }

        public boolean hasNext() {
            return null != this.next;
        }

        public List<SpeakerDeckPresentation> getPresentations() {
            return presentations;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        private CrawlResults(List<SpeakerDeckPresentation> presentations, int currentPage, URI next) {
            this.presentations = presentations;
            this.next = next;
            this.currentPage = currentPage;
        }
    }

    /**
     * Reads the results from a given page and gives the client back a (delegating) iterator.
     *
     * @see {@code http://www.w3.org/TR/2009/PR-css3-selectors-20091215/#attribute-representation}
     */
    private CrawlResults crawl(URI uri) {
        String htmlForPage = get(uri);

        logger.debug(String.format("crawling '%s'", uri.toString()));

        List<SpeakerDeckPresentation> presentations = new ArrayList<SpeakerDeckPresentation>();

        Document document = Jsoup.parse(htmlForPage, this.speakerDeckBaseUri);
        Elements publicTalks = document.select("[class=talk public]");
        for (org.jsoup.nodes.Element e : publicTalks) {

            SpeakerDeckAccount account = null;
            SpeakerDeckPresentation presentation = null;

            // account information
            for (org.jsoup.nodes.Element dateE : e.select("[class=date]")) {
                Elements aElement = dateE.select("a[href]");
                String humanName = aElement.text();
                String profileHref = aElement.first().absUrl("href");
                account = new SpeakerDeckAccount(URI.create(profileHref), humanName);
            }

            // title
            for (org.jsoup.nodes.Element titleE : e.select("[class=talk-listing-meta]").select("h3[class=title]")) {
                Element aElement = titleE.select("a").first();
                String title = aElement.getAllElements().text();
                String url = aElement.absUrl("href");
                String dataId = e.attr("data-id");
                String dataSlideCount = e.attr("data-slide-count");
                presentation = new SpeakerDeckPresentation(account, URI.create(url), dataId, title, Integer.parseInt(dataSlideCount));
            }
            presentations.add(presentation);
        }

        // let's find out the current page number

        Elements currentPageElement = document.select("[class=page current]");
        int currentPageNo = Integer.parseInt(currentPageElement.text().trim());

        // let's find out if there's a "next" page, and if so, what it is
        URI next = null;
        Elements nextElement = document.select("a[rel=next]");
        try {
            next = new URI(speakerDeckBaseUri + "" + nextElement.attr("href"));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        CrawlResults crawlResults = new CrawlResults(presentations, currentPageNo, next);
        logger.debug("crawlResults: " + crawlResults.toString());
        return crawlResults;
    }

    private class SpeakerDeckIterator implements Iterator<SpeakerDeckPresentation> {

        // todo can we use an Executor to "pre-fetch" the results in here?
        private URI uri;
        private CrawlResults crawlResults;
        private Iterator<SpeakerDeckPresentation> speakerDeckPresentationsIterator;

        private void loadFreshResults() {
            if (this.crawlResults == null) {
                this.crawlResults = crawl(this.uri);
                this.speakerDeckPresentationsIterator = this.crawlResults.getPresentations().iterator();
            }
        }

        public SpeakerDeckIterator(URI uri) {
            this.uri = uri;
        }

        @Override
        public boolean hasNext() {
            loadFreshResults();
            return this.speakerDeckPresentationsIterator.hasNext();
        }

        @Override
        public SpeakerDeckPresentation next() {
            loadFreshResults();
            return this.speakerDeckPresentationsIterator.next();
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