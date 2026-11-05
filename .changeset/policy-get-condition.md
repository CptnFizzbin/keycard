---
"@cptn-fizzbin/keycard": minor
---

Add `Policy.getCondition(action, subject)`, which returns the single condition a subject instance's claims must satisfy for the action to be allowed. Matching rules are combined with `$or`/`$and`/`$not` so the last matching rule still wins: evaluating the result against `claims` gives the same answer as `can(action, subject.wrap(claims))`. `{ $always: true }` means always allowed and `{ $never: true }` never allowed. Also exports the `InferClaims<TSubject>` type.
