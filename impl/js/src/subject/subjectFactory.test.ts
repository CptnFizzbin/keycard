import { describe, expect, test } from "vitest"

import { createSubject } from "./subjectFactory.ts"

describe("createSubject()", () => {
  test("called with a name behaves as before - not dynamic", () => {
    const subject = createSubject("Article")

    expect(subject.name).toBe("Article")
    expect(subject.__dynamic).toBeUndefined()
  })

  test("called with no name generates a random, usable id and marks it dynamic", () => {
    const subject = createSubject()

    expect(typeof subject.name).toBe("string")
    expect(subject.name.length).toBeGreaterThan(0)
    expect(subject.__dynamic).toBe(true)
  })

  test("each no-arg call generates a distinct id", () => {
    const a = createSubject()
    const b = createSubject()

    expect(a.name).not.toBe(b.name)
  })

  test("wrap() preserves the generated id and __dynamic marker", () => {
    const subject = createSubject<{ id: number }>()
    const wrapped = subject.wrap({ id: 1 })

    expect(wrapped.name).toBe(subject.name)
    expect(wrapped.__dynamic).toBe(true)
    expect(wrapped.instance).toEqual({ id: 1 })
  })
})
