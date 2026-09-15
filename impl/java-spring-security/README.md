# KeyCard - Spring Security

Spring Security integration for [KeyCard](../java/README.md): a
`PermissionEvaluator` and an `AuthorizationManager`, both backed by a
KeyCard `Policy`, so `@PreAuthorize`/`@PostAuthorize` SpEL and
`authorizeHttpRequests()` route rules can be driven by the same
PolicyDefinitions used everywhere else KeyCard runs.

This module never builds a `Policy` itself - your application supplies one
via a `KeycardPolicySource` (e.g. a `PolicyBuilder` run over JWT claims or
a role lookup), and this module wires that `Policy` into Spring Security's
extension points.

## Installation

```xml
<dependency>
    <groupId>com.cptnfizzbin</groupId>
    <artifactId>keycard-spring-security</artifactId>
    <version>0.0.1</version>
</dependency>
```

Spring Security itself (`spring-security-core`, and `spring-security-web`
if you use `KeycardAuthorizationManager`) is a `provided` dependency - your
application supplies its own version, this module just compiles against
one.

## Quick Start

### 1. Supply a `KeycardPolicySource`

```java
import com.cptnfizzbin.keycard.spring.CachingPolicySource;
import com.cptnfizzbin.keycard.spring.KeycardPolicySource;

@Bean
public KeycardPolicySource keycardPolicySource() {
    KeycardPolicySource builds = authentication -> {
        // Read claims/roles off `authentication` however your app does -
        // a JWT's claims, a DB-loaded role set, etc - and run them
        // through a PolicyBuilder (see ../java/README.md).
        return new PolicyBuilder()
            .allow(ActionFactory.create("Read"), SubjectFactory.create("Article"))
            .allow(ActionFactory.create("Update"), SubjectFactory.create("Article"),
                Map.of("ownerId", currentUserId(authentication)))
            .build();
    };

    // Optional: avoid rebuilding the Policy on every hasPermission() call.
    return new CachingPolicySource(builds, Authentication::getName);
}
```

### 2. Register the `PermissionEvaluator` for method security

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(KeycardPolicySource policySource) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(new KeycardPermissionEvaluator(policySource));
        return handler;
    }
}
```

### 3. Use it from `@PreAuthorize`/`@PostAuthorize`

```java
@PreAuthorize("hasPermission(#article, 'Update')")
public void updateArticle(Article article) { ... }

@PreAuthorize("hasPermission(#id, 'Article', 'Delete')")
public void deleteArticle(Long id) { ... }

@PostAuthorize("hasPermission(returnObject, 'Read')")
public Article getArticle(Long id) { ... }
```

The two-arg form (`hasPermission(#article, 'Update')`) wraps a loaded
domain object as a KeyCard `Subject` instance, so instance-aware
Conditions (e.g. `ownerId`) can evaluate against it. The three-arg form
(`hasPermission(#id, 'Article', 'Delete')`) has no instance to load, so
it's necessarily a bare, no-instance check - a rule with Conditions can
never match it (SPEC_V1-0-0.md EC-7/EC-9). Reach for the two-arg form once
an instance is available.

### 4. Optional: secure routes in the filter chain

For a coarse check before any domain object is loaded - e.g. gating a
whole route by action + subject type:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http, KeycardPolicySource policySource) throws Exception {
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/articles/**")
            .access(new KeycardAuthorizationManager(policySource, "Read", "Article"))
    );
    return http.build();
}
```

Like the three-arg `hasPermission(...)`, this is a bare, no-instance
check.

## Subject naming

By default, `KeycardPermissionEvaluator` names an instance's `Subject`
after its Java simple class name (`Article` for `com.example.Article`),
and a bare `hasPermission(id, type, permission)` check after `type`
verbatim. If your PolicyDefinitions use different Subject names, supply a
custom `KeycardSubjectResolver`:

```java
new KeycardPermissionEvaluator(policySource, new DefaultSubjectResolver(obj -> switch (obj) {
    case Article a -> "Article";
    case Comment c -> "Comment";
    default -> obj.getClass().getSimpleName();
}));
```

## API

- **`KeycardPolicySource`** - `Policy policyFor(Authentication)`. The one
  thing every application implements: how claims/roles become a `Policy`.
- **`CachingPolicySource`** - wraps a `KeycardPolicySource`, caching by a
  caller-supplied key (e.g. `Authentication::getName`). Unbounded, no TTL
  - wrap with an eviction-aware cache (e.g. Caffeine) if your key space is
  unbounded.
- **`KeycardSubjectResolver`** - maps a domain object, or an id + type
  pair, to a KeyCard `Subject`. `DefaultSubjectResolver` covers the common
  case (Subject name = simple class name / the type string).
- **`KeycardPermissionEvaluator`** - Spring Security `PermissionEvaluator`
  backing `hasPermission(...)` in SpEL.
- **`KeycardAuthorizationManager`** - `AuthorizationManager<RequestAuthorizationContext>`
  for securing routes with a fixed action/subject pair.

## See Also

- [../java/README.md](../java/README.md) - the KeyCard Java library this
  module adapts.
- [../../SPEC.md](../../SPEC.md) - the KeyCard specification overview.
