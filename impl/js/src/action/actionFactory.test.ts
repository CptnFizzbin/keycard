import { describe, expect, test } from "vitest"

import { createAction } from "./actionFactory.ts"

describe("createAction()", () => {
  test("called with a name behaves as before - not dynamic", () => {
    const action = createAction("Read")

    expect(action.name).toBe("Read")
    expect(action.__dynamic).toBeUndefined()
  })

  test("called with no name generates a random, usable id and marks it dynamic", () => {
    const action = createAction()

    expect(typeof action.name).toBe("string")
    expect(action.name.length).toBeGreaterThan(0)
    expect(action.__dynamic).toBe(true)
  })

  test("each no-arg call generates a distinct id", () => {
    const a = createAction()
    const b = createAction()

    expect(a.name).not.toBe(b.name)
  })
})
