package com.cptnfizzbin.keycard.examples.vision.realbackend;

/**
 * Policy Claims: actor-side data that decides which rules a policy even
 * generates. Never carries resource data.
 */
public record PolicyClaims(String userId, String orgId, Role role) {
}
