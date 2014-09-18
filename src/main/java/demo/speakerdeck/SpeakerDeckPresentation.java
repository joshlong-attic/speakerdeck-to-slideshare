package demo.speakerdeck;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.net.URI;

public class SpeakerDeckPresentation implements Serializable {

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
