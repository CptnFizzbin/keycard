import { describe, expect, test } from "vitest"

import { PolicyBuilder } from "./policyBuilder.ts"
import { createAction } from "../action/index.ts"
import { createOperator } from "../conditions/index.ts"
import { PolicyArgumentError } from "../errors/index.ts"
import { createSubject } from "../subject/index.ts"

describe("PolicyBuilder: meta.actions/subjects/operators are derived from usage", () => {
  test("buildDef() derives actions/subjects/operators from what was actually used", () => {
    const article = createSubject("Article")
    const user = createSubject("User")
    const read = createAction("Read")
    const update = createAction("Update")
    const hasRole = createOperator("$hasRole", () => true)

    const def = new PolicyBuilder({ operators: [hasRole] })
      .allow(read, article)
      .allow(update, user, { $hasRole: "admin" })
      .buildDef()

    expect(def.meta?.actions).toEqual(["Read", "Update"])
    expect(def.meta?.subjects).toEqual(["Article", "User"])
    expect(def.meta?.operators).toEqual(["$hasRole"])
  })

  test("leaves the wildcard tokens undeclared by default", () => {
    const def = new PolicyBuilder()
      .allow(createAction("Read"), createSubject("Article"))
      .buildDef()

    // Undeclared -> the §3.2.1 "_ANY_" default applies - an options-less
    // PolicyBuilder MUST NOT come out as "explicitly disabled" (that's
    // what an explicit null does).
    expect(def.meta?.anyAction).toBeUndefined()
    expect(def.meta?.anySubject).toBeUndefined()
  })

  test("the options constructor declares just the tokens requested", () => {
    const policy = new PolicyBuilder({ anyAction: "*", anySubject: null })
      .allow(createAction("*"), createSubject("Article"))
      .allow(createAction("Read"), createSubject("*"))
      .build()

    // "*" is now the action wildcard token: a rule naming it as its
    // action matches any incoming action.
    expect(policy.can(createAction("AnythingGoes"), createSubject("Article"))).toBe(true)

    // The subject wildcard is disabled (null): a rule's literal "*"
    // subject only matches an incoming subject also literally named "*".
    expect(policy.can(createAction("Read"), createSubject("AnySubjectName"))).toBe(false)
    expect(policy.can(createAction("Read"), createSubject("*"))).toBe(true)
  })

  test("still catches EC-6 at addRule time with the options constructor", () => {
    expect(() =>
      new PolicyBuilder({ anyAction: "*", anySubject: "*" })
        // @ts-expect-error -- specifically testing an invalid type
        .allow(createAction("*"), createSubject("*"), { owner_id: 1 }),
    ).toThrow(PolicyArgumentError)
  })

  test("accepts an Action/Subject (not just a bare string) for the wildcard options", () => {
    const anyAction = createAction("*")
    const anySubject = createSubject("*")

    const policy = new PolicyBuilder({ anyAction, anySubject })
      .allow(createAction("*"), createSubject("*"))
      .build()

    expect(policy.can(createAction("Anything"), createSubject("Anything"))).toBe(true)
  })

  test("KeycardConfig.actions/subjects are folded into meta.actions/meta.subjects alongside what usage derives", () => {
    const def = new PolicyBuilder({}, { actions: [createAction("Delete")], subjects: [createSubject("Comment")] })
      .allow(createAction("Read"), createSubject("Article"))
      .buildDef()

    expect(def.meta?.actions).toEqual(["Read", "Delete"])
    expect(def.meta?.subjects).toEqual(["Article", "Comment"])
  })

  test("KeycardConfig.operators is used in place of the options constructor's operators", () => {
    const hasRole = createOperator("$hasRole", () => true)
    const article = createSubject<{ id: number }>("Article")

    const policy = new PolicyBuilder({}, { operators: [hasRole] })
      .allow(createAction("Read"), article, { $hasRole: "admin" })
      .build()

    expect(policy.can(createAction("Read"), article.wrap({ id: 1 }))).toBe(true)
  })
})
