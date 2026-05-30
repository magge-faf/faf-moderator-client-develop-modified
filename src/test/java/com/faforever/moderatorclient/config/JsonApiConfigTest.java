package com.faforever.moderatorclient.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class JsonApiConfigTest {

    private final ObjectMapper objectMapper = new JsonApiConfig().objectMapper();

    @Test
    void objectMapperUsesApplicationDefaults() {
        assertThat(objectMapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion(),
                is(JsonInclude.Include.NON_NULL));
        assertThat(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS), is(false));
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), is(false));
        assertThat(objectMapper.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE), is(true));
    }

    @Test
    void serializesJavaTimeValuesAsIsoStrings() throws Exception {
        DatedValue value = new DatedValue(OffsetDateTime.parse("2026-05-28T14:30:00+02:00"));

        assertThat(objectMapper.writeValueAsString(value), containsString("\"time\":\"2026-05-28T14:30:00+02:00\""));
    }

    @Test
    void deserializesComparableVersion() throws Exception {
        VersionedValue value = objectMapper.readValue("{\"version\":\"1.2.3-beta\"}", VersionedValue.class);

        assertThat(value.version.toString(), is("1.2.3-beta"));
    }

    @Test
    void ignoresUnknownProperties() throws Exception {
        VersionedValue value = objectMapper.readValue(
                "{\"version\":\"2.0.0\",\"unexpected\":\"ignored\"}",
                VersionedValue.class
        );

        assertThat(value.version.toString(), is("2.0.0"));
    }

    private record DatedValue(OffsetDateTime time) {
    }

    private static class VersionedValue {
        public ComparableVersion version;
    }
}
