package demo.speakerdeck;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SpeakerDeckClient {

    private final URI speakerDeckBaseUri = URI.create("https://speakerdeck.com");

    private final CrawlUtils crawlUtils;

    @Autowired
    public SpeakerDeckClient(CrawlUtils crawlUtils) {
        this.crawlUtils = crawlUtils;
    }

    public Iterator<SpeakerDeckPresentation> userPresentations(String user) {
        URI uri = URI.create(speakerDeckBaseUri + "/" + user);
        UserSpeakerDeckCrawler userCrawler = new UserSpeakerDeckCrawler(uri, this.crawlUtils);
        return new SpeakerDeckIterator(userCrawler);
    }

    public Iterator<SpeakerDeckPresentation> searchResultPresentations(String query) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(speakerDeckBaseUri + "/search")
                .queryParam("q", query)
                .build()
                .toUri();
        SearchSpeakerDeckCrawler searchCrawler = new SearchSpeakerDeckCrawler(uri, this.crawlUtils);
        return new SpeakerDeckIterator(searchCrawler);
    }
}

@Component
class CrawlUtils {

    private final RestTemplate restTemplate;

    @Autowired
    public CrawlUtils(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String fileNameForURI(URI uri) {
        String uriStr = uri.toString();
        String nString = "";
        for (char c : uriStr.toCharArray()) {
            nString += Character.isLetterOrDigit(c) ? c : "";
        }
        return nString + ".html";
    }

    public String get(URI uri) {
        try {
            String content = null;

            File tmpDir = SystemUtils.getJavaIoTmpDir();
            File fileForUri = new File(tmpDir, fileNameForURI(uri));
            if (fileForUri.exists()) {
                return FileUtils.readFileToString(fileForUri);
            } else {
                content = doGet(uri);
                try (FileWriter fw = new FileWriter(fileForUri)) {
                    IOUtils.write(content, fw);
                }
            }
            if (content == null) {
                content = doGet(uri);
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public String doGet(URI uri) {
        ResponseEntity<String> responseEntity = restTemplate.exchange(
                uri, HttpMethod.GET, null, new ParameterizedTypeReference<String>() {
                });
        return responseEntity.getBody();
    }

}

interface SpeakerDeckCrawler {

    CrawlResults crawl();

    default List<SpeakerDeckPresentation> speakerDeckPresentations(Document document) {
        return document.select("[class=talk public]").stream().map(e -> presentation(e, account(e))).collect(Collectors.toList());
    }

    default SpeakerDeckPresentation presentation(Element e, SpeakerDeckAccount account) {
        SpeakerDeckPresentation presentation = null;
        // title
        for (Element titleE : e.select("[class=talk-listing-meta]").select("h3[class=title]")) {
            Element aElement = titleE.select("a").first();
            String title = aElement.getAllElements().text();
            String url = aElement.absUrl("href");
            String dataId = e.attr("data-id");
            String dataSlideCount = e.attr("data-slide-count");
            presentation = new SpeakerDeckPresentation(account, URI.create(url), dataId, title, Integer.parseInt(dataSlideCount));
        }
        return presentation;
    }

    default SpeakerDeckAccount account(Element e) {

        for (Element dateE : e.select("[class=date]")) {
            Elements aElement = dateE.select("a[href]");
            String humanName = aElement.text();
            String profileHref = aElement.first().absUrl("href");
            return new SpeakerDeckAccount(URI.create(profileHref), humanName);
        }
        return null;
    }

    default int currentPageNo(Document document) {
        Elements currentPageElement = document.select("[class=page current]");
        String currentTextForPageNo = currentPageElement.text().trim();
        return StringUtils.hasText(currentTextForPageNo) ? Integer.parseInt(currentTextForPageNo) : 0;
    }

    default URI next(URI speakerDeckBaseUri, Document document) {
        Elements nextElement = document.select("a[rel=next]");
        if (nextElement != null && nextElement.size() != 0) {
            try {
                return new URI(speakerDeckBaseUri + "" + nextElement.attr("href"));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}

class UserSpeakerDeckCrawler implements SpeakerDeckCrawler {

    private final URI speakerDeckBaseUri;
    private final CrawlUtils crawlUtils;

    public UserSpeakerDeckCrawler(URI uri, CrawlUtils crawlUtils) {
        this.speakerDeckBaseUri = uri;
        this.crawlUtils = crawlUtils;
    }

    @Override
    public CrawlResults crawl() {
        String htmlForPage = crawlUtils.get(speakerDeckBaseUri);
        Document document = Jsoup.parse(htmlForPage, this.speakerDeckBaseUri.toString());
        List<SpeakerDeckPresentation> speakerDeckPresentations = this.speakerDeckPresentations(document);
        return new CrawlResults(
                speakerDeckPresentations,
                currentPageNo(document),
                next(speakerDeckBaseUri, document));
    }

}

class SearchSpeakerDeckCrawler implements SpeakerDeckCrawler {

    private final URI speakerDeckBaseUri;

    private final CrawlUtils crawlUtils;

    public SearchSpeakerDeckCrawler(URI uri, CrawlUtils crawlUtils) {
        this.speakerDeckBaseUri = uri;
        this.crawlUtils = crawlUtils;
    }

    @Override
    public CrawlResults crawl() {
        String htmlForPage = crawlUtils.get(speakerDeckBaseUri);
        Document document = Jsoup.parse(htmlForPage, this.speakerDeckBaseUri.toString());
        List<SpeakerDeckPresentation> speakerDeckPresentations = this.speakerDeckPresentations(document);
        return new CrawlResults(
                speakerDeckPresentations,
                currentPageNo(document),
                next(speakerDeckBaseUri, document));
    }

}

class SpeakerDeckIterator implements Iterator<SpeakerDeckPresentation> {

    private final SpeakerDeckCrawler speakerDeckCrawler;
    private CrawlResults crawlResults;
    private Iterator<SpeakerDeckPresentation> speakerDeckPresentationsIterator;

    private void loadFreshResults() {
        if (this.crawlResults == null) {
            synchronized (this) {
                this.crawlResults = this.speakerDeckCrawler.crawl();
                this.speakerDeckPresentationsIterator =
                        this.crawlResults.getPresentations().iterator();
            }
        }
    }

    public SpeakerDeckIterator(SpeakerDeckCrawler speakerDeckCrawler) {
        this.speakerDeckCrawler = speakerDeckCrawler;
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

class CrawlResults {
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

    public CrawlResults(List<SpeakerDeckPresentation> presentations, int currentPage, URI next) {
        this.presentations = presentations;
        this.next = next;
        this.currentPage = currentPage;
    }
}
