package demo.speakerdeck;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.net.URI;

public class SpeakerDeckAccount {

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
