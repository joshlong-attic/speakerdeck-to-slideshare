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
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class SpeakerDeckClient {

    private final URI speakerDeckBaseUri = URI.create("https://speakerdeck.com");

    private final SpeakerDeckCrawler speakerDeckCrawler;

    @Autowired
    public SpeakerDeckClient(SpeakerDeckCrawler speakerDeckCrawler) {
        this.speakerDeckCrawler = speakerDeckCrawler;
    }

    public Iterator<SpeakerDeckPresentation> userPresentations(String username) throws Exception {
        URI uri = URI.create(speakerDeckBaseUri + "/" + username);
        return new SpeakerDeckReloadingIterator(this.speakerDeckCrawler, uri);
    }


    public Iterator<SpeakerDeckPresentation> searchResultPresentations(String query) throws Exception {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(speakerDeckBaseUri + "/search")
                .queryParam("q", query)
                .build()
                .toUri();
        return new SpeakerDeckReloadingIterator(this.speakerDeckCrawler, uri);
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

@Component
class SpeakerDeckCrawler {

    private final CrawlUtils crawlUtils;

    protected List<SpeakerDeckPresentation> speakerDeckPresentations(Document document) {
        return document.select("[class=talk public]").stream().map(e -> presentation(e, account(e))).collect(Collectors.toList());
    }

    protected SpeakerDeckPresentation presentation(Element e, SpeakerDeckAccount account) {
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

    protected SpeakerDeckAccount account(Element e) {

        for (Element dateE : e.select("[class=date]")) {
            Elements aElement = dateE.select("a[href]");
            String humanName = aElement.text();
            String profileHref = aElement.first().absUrl("href");
            return new SpeakerDeckAccount(URI.create(profileHref), humanName);
        }
        return null;
    }

    protected int currentPageNo(Document document) {
        Elements currentPageElement = document.select("[class=page current]");
        String currentTextForPageNo = currentPageElement.text().trim();
        return StringUtils.hasText(currentTextForPageNo) ? Integer.parseInt(currentTextForPageNo) : 0;
    }

    protected URI next(URI speakerDeckBaseUri, Document document) {
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

    @Autowired
    public SpeakerDeckCrawler(CrawlUtils crawlUtils) {
        this.crawlUtils = crawlUtils;
    }

    public CrawlResults crawl(URI speakerDeckBaseUri) {
        String htmlForPage = crawlUtils.get(speakerDeckBaseUri);
        Document document = Jsoup.parse(htmlForPage, speakerDeckBaseUri.toString());
        return new CrawlResults(
                this.speakerDeckPresentations(document),
                this.currentPageNo(document),
                this.next(speakerDeckBaseUri, document));
    }
}

class SpeakerDeckReloadingIterator extends ReloadingIterator<SpeakerDeckPresentation> {
    private SpeakerDeckCrawler speakerDeckCrawler;
    private URI start;

    public SpeakerDeckReloadingIterator(SpeakerDeckCrawler speakerDeckCrawler, URI start) {
        this.start = start;
        this.speakerDeckCrawler = speakerDeckCrawler;
    }

    @Override
    public Collection<SpeakerDeckPresentation> more() {
        CrawlResults crawlResults;
        if ((crawlResults = this.speakerDeckCrawler.crawl(this.start)) != null) {
            this.start = crawlResults.getNext();
            return crawlResults.getPresentations();
        }
        return null;
    }
}

abstract class ReloadingIterator<T> implements Iterator<T> {

    public ReloadingIterator() {
        this.collection = more();

        this.iterator = this.collection.iterator();
    }

    private Collection<T> collection;
    private Iterator<T> iterator;

    public abstract Collection<T> more();

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove () isn't supported!");
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        while (hasNext())
            action.accept(next());
    }

    void refill() {
        Collection<T> ts = more();
        if (ts == null || ts.size() == 0) {
            collection = new ArrayList<>();
        } else {
            collection = ts;
        }
        iterator = collection.iterator();
    }

    @Override
    public boolean hasNext() {
        if (!this.iterator.hasNext())
            this.refill();

        return this.iterator.hasNext();
    }

    @Override
    public T next() {
        return this.iterator.next();
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

    public CrawlResults(List<SpeakerDeckPresentation> presentations, int currentPage,
                        URI next) {
        this.presentations = presentations;
        this.next = next;
        this.currentPage = currentPage;
    }
}
