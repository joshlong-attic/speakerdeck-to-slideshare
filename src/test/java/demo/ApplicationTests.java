package demo;

import demo.speakerdeck.SpeakerDeckClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
public class ApplicationTests {

    @Autowired
    private SpeakerDeckClient speakerDeckClient;

    @Test
    public void testSpeakerDeckUserPresentations() {
        this.speakerDeckClient.userPresentations("joshlong").
                forEachRemaining(presentation -> System.out.println("found " + presentation.toString()));
    }

    @Test
    public void testSearchResultPresentations() throws Throwable {
        this.speakerDeckClient.searchResultPresentations("Spring Framework").
                forEachRemaining(presentation -> System.out.println("found " + presentation.toString()));
    }

}
