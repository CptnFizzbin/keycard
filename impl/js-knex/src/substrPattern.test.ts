import { describe, expect, test } from "vitest"

import { parseSubstrPattern, toGlobPattern, toLikePattern } from "./substrPattern.ts"

describe("parseSubstrPattern", () => {
  test.each([
    ["mid^dle"],
    ["mid$dle"],
    ["$start"],
  ])("rejects the malformed pattern %j", (pattern) => {
    expect(parseSubstrPattern(pattern)).toBeNull()
  })

  test("ignores a trailing lone backslash", () => {
    // Arrange
    const pattern = parseSubstrPattern("abc\\")!

    // Act
    const like = toLikePattern(pattern)

    // Assert
    expect(like).toBe("%abc%")
  })
})

describe("toLikePattern", () => {
  test.each([
    ["Draft", "%Draft%"],
    ["^Draft", "Draft%"],
    ["Draft$", "%Draft"],
    ["^Draft$", "Draft"],
    ["^Draft:*end$", "Draft:%end"],
    ["100%_[x]!", "%100!%!_![x]!!%"],
    ["\\^\\*\\$", "%^*$%"],
  ])("translates %j to %j", (pattern, expected) => {
    expect(toLikePattern(parseSubstrPattern(pattern)!)).toBe(expected)
  })
})

describe("toGlobPattern", () => {
  test.each([
    ["Draft", "*Draft*"],
    ["^Draft$", "Draft"],
    ["^a*b", "a*b*"],
    ["*a*", "*a*"],
    ["what?[x]", "*what[?][[]x]*"],
    ["\\*", "*[*]*"],
  ])("translates %j to %j", (pattern, expected) => {
    expect(toGlobPattern(parseSubstrPattern(pattern)!)).toBe(expected)
  })
})
