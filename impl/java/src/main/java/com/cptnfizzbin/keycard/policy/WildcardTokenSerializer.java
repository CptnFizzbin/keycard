package com.cptnfizzbin.keycard.policy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Writes a {@link WildcardToken} back out as the raw scalar SPEC_V0.md
 * §3.2.1 form it was read from - a {@link WildcardToken.Named}'s token
 * string, or {@code null} for {@link WildcardToken.Disabled} - never the
 * record's own field shape. Paired with {@link WildcardTokenDeserializer}
 * so a {@link PolicyDefinition} round-trips through Jackson unchanged.
 */
final class WildcardTokenSerializer extends JsonSerializer<WildcardToken> {
    @Override
    public void serialize(WildcardToken value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value instanceof WildcardToken.Named named) {
            gen.writeString(named.token());
        } else {
            gen.writeNull();
        }
    }
}
