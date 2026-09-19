package com.cptnfizzbin.keycard.policy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Binds a raw {@code anyAction}/{@code anySubject} value - a string,
 * {@code null}, {@code false}, or anything else - to a {@link
 * WildcardToken} via {@link WildcardToken#of}, applying SPEC_V0.md
 * four-way dispatch. {@link #getNullValue} is what Jackson
 * calls for an *explicit* null the property declares; Jackson never
 * calls either method when the property is absent altogether, which is
 * what lets {@link PolicyDefinition.Meta#anyAction()}/{@code
 * anySubject()} tell "declared null" (disables the wildcard) apart from
 * "not declared at all" (stays {@code null}) once bound straight from a
 * document.
 */
final class WildcardTokenDeserializer extends JsonDeserializer<WildcardToken> {
    @Override
    public WildcardToken deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return WildcardToken.of(p.readValueAs(Object.class));
    }

    @Override
    public WildcardToken getNullValue(DeserializationContext ctxt) {
        return WildcardToken.of(null);
    }
}
