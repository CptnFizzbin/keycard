import { describe, expect, test, vi } from "vitest"

import type { OperatorContext } from "../operator.ts"
import { SubstrOperator } from "./substrOperator.ts"

describe("$substr", () => {
  const $substr = SubstrOperator
  const ctx: OperatorContext = { resolveSubcondition: vi.fn() }

  describe("literal matching", () => {
    test.each([
      { pattern: "foobar", subject: "foobar", match: true },
      { pattern: "foo", subject: "foobar", match: true },
      { pattern: "bar", subject: "foobar", match: true },
      { pattern: "oob", subject: "foobar", match: true },
      { pattern: "foobar", subject: "fizzbuzz", match: false },
      { pattern: "xyz", subject: "foobar", match: false },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("wildcard (*) - matches zero or more characters", () => {
    test.each([
      { pattern: "foo*bar", subject: "foobar", match: true },
      { pattern: "foo*bar", subject: "foodybar", match: true },
      { pattern: "foo*bar", subject: "foo_bar", match: true },
      { pattern: "foo*bar", subject: "foo123bar", match: true },
      { pattern: "f*r", subject: "foobar", match: true },
      { pattern: "*bar", subject: "foobar", match: true },
      { pattern: "foo*", subject: "foobar", match: true },
      { pattern: "foo*baz", subject: "foobar", match: false },
      { pattern: "foo*bar", subject: "foobr", match: false },
      { pattern: "*", subject: "", match: true },
      { pattern: "*", subject: "anything", match: true },
      { pattern: "a*", subject: "a", match: true },
      { pattern: "a*", subject: "ab", match: true },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("start anchor (^)", () => {
    test.each([
      { pattern: "^foobar", subject: "foobar", match: true },
      { pattern: "^foo", subject: "foobar", match: true },
      { pattern: "^foo*bar", subject: "foobar", match: true },
      { pattern: "^foo*bar", subject: "foodybar", match: true },
      { pattern: "^bar", subject: "foobar", match: false },
      { pattern: "^foo", subject: "prefoobar", match: false },
      { pattern: "^foo*baz", subject: "foobar", match: false },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("end anchor ($)", () => {
    test.each([
      { pattern: "foobar$", subject: "foobar", match: true },
      { pattern: "bar$", subject: "foobar", match: true },
      { pattern: "foo*bar$", subject: "foobar", match: true },
      { pattern: "foo*bar$", subject: "foodybar", match: true },
      { pattern: "foo$", subject: "foobar", match: false },
      { pattern: "bar$", subject: "foobarqux", match: false },
      { pattern: "foo*baz$", subject: "foobar", match: false },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("both anchors (^...$)", () => {
    test.each([
      { pattern: "^foobar$", subject: "foobar", match: true },
      { pattern: "^foo*bar$", subject: "foobar", match: true },
      { pattern: "^foo*bar$", subject: "foodybar", match: true },
      { pattern: "^foobar$", subject: "xfoobar", match: false },
      { pattern: "^foobar$", subject: "foobarx", match: false },
      { pattern: "^foobar$", subject: "xfoobarx", match: false },
      { pattern: "^foo$", subject: "foobar", match: false },
      { pattern: "^*$", subject: "xyz", match: true },
      { pattern: "^*$", subject: "", match: true },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("escape sequences", () => {
    test.each([
      { pattern: "foo\\*bar", subject: "foo*bar", match: true },
      { pattern: "foo\\*bar", subject: "fooXbar", match: false },
      { pattern: "\\^anchor", subject: "^anchor", match: true },
      { pattern: "\\$end", subject: "$end", match: true },
      { pattern: "\\\\backslash", subject: "\\backslash", match: true },
      { pattern: "foo\\^bar", subject: "foo^bar", match: true },
      { pattern: "end\\$", subject: "end$", match: true },
      { pattern: "quote\\e", subject: "quotee", match: true },
      { pattern: "foo\\", subject: "foo", match: true },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("multiple wildcards", () => {
    test.each([
      { pattern: "a*b*c", subject: "aXbYc", match: true },
      { pattern: "a*b*c", subject: "abc", match: true },
      { pattern: "a*b*c", subject: "aXbYcZ", match: true },
      { pattern: "a*b*c", subject: "abXc", match: true },
      { pattern: "a*b*c*d", subject: "abcd", match: true },
      { pattern: "a*b*c*d", subject: "aXbYcZd", match: true },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("null/undefined handling", () => {
    test.each([
      { pattern: "test", subject: null, match: false },
      { pattern: "test", subject: undefined, match: false },
      { pattern: "^test$", subject: null, match: false },
      { pattern: "^test$", subject: undefined, match: false },
    ])("pattern '$pattern' matches undefined/null subject: $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })

  describe("edge cases", () => {
    test.each([
      { pattern: "", subject: "", match: true },
      { pattern: "", subject: "anything", match: true },
      { pattern: "^$", subject: "", match: true },
      { pattern: "^$", subject: "anything", match: false },
      { pattern: "a", subject: "a", match: true },
    ])("pattern '$pattern' matches '$subject': $match", ({ pattern, subject, match }) => {
      expect($substr.resolve(subject, pattern, ctx)).toBe(match)
    })
  })
})
