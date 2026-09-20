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

    // Undeclared -> the "_ANY_" default applies - a config-less
    // PolicyBuilder MUST NOT come out as "explicitly disabled" (that's
    // what an explicit null does).
    expect(def.meta?.anyAction).toBeUndefined()
    expect(def.meta?.anySubject).toBeUndefined()
  })

  test("the config constructor declares just the tokens requested", () => {
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

  test("still catches EC-6 at addRule time with the config constructor", () => {
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
    const def = new PolicyBuilder({ actions: { Delete: createAction("Delete") }, subjects: { Comment: createSubject("Comment") } })
      .allow(createAction("Read"), createSubject("Article"))
      .buildDef()

    expect(def.meta?.actions).toEqual(["Read", "Delete"])
    expect(def.meta?.subjects).toEqual(["Article", "Comment"])
  })

  test("KeycardConfig.operators accepts an OperatorCatalog (bare resolver functions) as well as an AnyOperator[]", () => {
    const article = createSubject<{ id: number }>("Article")

    const policy = new PolicyBuilder({ operators: { $hasRole: () => true } })
      .allow(createAction("Read"), article, { $hasRole: "admin" })
      .build()

    expect(policy.can(createAction("Read"), article.wrap({ id: 1 }))).toBe(true)
  })
})

describe("PolicyBuilder: dynamic (no-name) Action/Subject resolved via a KeycardConfig catalog", () => {
  test("a keyed catalog's key - not the dynamic def's random id - is what gets serialized", () => {
    const create = createAction()
    const article = createSubject()

    const def = new PolicyBuilder({ actions: { create }, subjects: { article } })
      .allow(create, article)
      .buildDef()

    expect(def.rules).toEqual([["allow", "create", "article"]])
    expect(def.meta?.actions).toEqual(["create"])
    expect(def.meta?.subjects).toEqual(["article"])
  })

  test("allow() throws PolicyArgumentError for a dynamic Action never registered in the catalog", () => {
    const create = createAction()
    const article = createSubject("Article")

    expect(() =>
      new PolicyBuilder({ actions: { update: createAction() } }).allow(create, article),
    ).toThrow(PolicyArgumentError)
  })

  test("allow() throws PolicyArgumentError for a dynamic Subject never registered in the catalog", () => {
    const read = createAction("Read")
    const article = createSubject()

    expect(() => new PolicyBuilder().allow(read, article)).toThrow(PolicyArgumentError)
  })

  test("registering the same dynamic Action under two different catalog keys throws at construction", () => {
    const create = createAction()

    expect(() => new PolicyBuilder({ actions: { create, submit: create } })).toThrow(PolicyArgumentError)
  })

  test("an explicitly-named Action/Subject in a keyed catalog is still resolved to its catalog key", () => {
    // "if using a catalog, defining the name is optional" - a catalog key
    // wins for any entry, named or not.
    const create = createAction("Create")
    const article = createSubject("Article")

    const def = new PolicyBuilder({ actions: { submit: create }, subjects: { post: article } })
      .allow(create, article)
      .buildDef()

    expect(def.rules).toEqual([["allow", "submit", "post"]])
  })
})

describe("PolicyBuilder: emitMeta", () => {
  test("defaults to true - meta.actions/subjects/operators are emitted, and catalog/registration errors are eager", () => {
    const def = new PolicyBuilder()
      .allow(createAction("Read"), createSubject("Article"))
      .buildDef()

    expect(def.meta?.actions).toEqual(["Read"])
    expect(def.meta?.subjects).toEqual(["Article"])

    expect(() => new PolicyBuilder().allow(createAction(), createSubject("Article"))).toThrow(PolicyArgumentError)
  })

  test("false: meta.actions/subjects/operators are omitted, but functional anyAction/anySubject overrides are still emitted", () => {
    const def = new PolicyBuilder({ emitMeta: false, anyAction: "*" })
      .allow(createAction("Read"), createSubject("Article"))
      .buildDef()

    expect(def.meta?.actions).toBeUndefined()
    expect(def.meta?.subjects).toBeUndefined()
    expect(def.meta?.operators).toBeUndefined()
    expect(def.meta?.anyAction).toBe("*")
  })

  test("false: a dynamic Action/Subject never registered in any catalog no longer throws at addRule time", () => {
    const create = createAction()
    const article = createSubject()

    expect(() =>
      new PolicyBuilder({ emitMeta: false }).allow(create, article),
    ).not.toThrow()
  })

  test("false: a duplicate catalog key no longer throws at construction", () => {
    const create = createAction()

    expect(() =>
      new PolicyBuilder({ emitMeta: false, actions: { create, submit: create } }),
    ).not.toThrow()
  })
})
