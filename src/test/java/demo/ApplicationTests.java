package demo;

import demo.speakerdeck.SpeakerDeckClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class ApplicationTests {

    @Autowired
    private SpeakerDeckClient speakerDeckClient;
    private AtomicInteger atomicInteger;

    @Before
    public void before() {
        this.atomicInteger = new AtomicInteger();
    }

    int next() {
        return this.atomicInteger.incrementAndGet();
    }

    /*@Test
    public void testSpeakerDeckUserPresentations() {
        this.speakerDeckClient.userPresentations("joshlong").
                forEachRemaining(presentation -> System.out.println(next() + " - found " + presentation.toString()));
    }*/

    @Test
    public void testSearchResultPresentations() throws Throwable {
        this.speakerDeckClient.searchResultPresentations("bats").
                forEachRemaining(presentation -> System.out.println(next() + " - found " + presentation.toString()));
    }

}
