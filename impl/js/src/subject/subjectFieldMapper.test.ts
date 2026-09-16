import { describe, expect, test } from "vitest"

import { createSubject } from "./subjectFactory.ts"
import { SubjectFieldMapperCatalog } from "./subjectFieldMapperCatalog.ts"
import { createAction } from "../action/index.ts"
import { Policy } from "../policy/policy.ts"

interface Post {
  status: string
  author: { name: string }
}

const Read = createAction("Read")

describe("SubjectFieldMapper: attached via createSubject", () => {
  test("a mapped field is resolved through its getter instead of property access", () => {
    const post = createSubject<Post>("Post", {
      authorName: (instance) => instance.author.name,
    })

    const policy = Policy.from({
      version: "1.0.0",
      rules: [["allow", "Read", "Post", { authorName: "Alice" }]],
    })

    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Alice" } }))).toBe(true)
    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Bob" } }))).toBe(false)
  })

  test("a field the mapper doesn't define still falls back to ordinary property access", () => {
    const post = createSubject<Post>("Post", {
      authorName: (instance) => instance.author.name,
    })

    const policy = Policy.from({
      version: "1.0.0",
      rules: [["allow", "Read", "Post", { status: "draft" }]],
    })

    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Alice" } }))).toBe(true)
    expect(policy.can(Read, post.wrap({ status: "published", author: { name: "Alice" } }))).toBe(false)
  })

  test("does not apply once a field lookup has narrowed into a nested value", () => {
    // The mapper is keyed for the top-level Post instance - `author.name`
    // still resolves via plain property access on the narrowed `author`
    // object, not through the mapper (which isn't even asked about "name").
    const post = createSubject<Post>("Post", {
      authorName: (instance) => instance.author.name,
    })

    const policy = Policy.from({
      version: "1.0.0",
      rules: [["allow", "Read", "Post", { author: { name: "Alice" } }]],
    })

    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Alice" } }))).toBe(true)
  })

  test("still applies inside $and/$or/$not, which evaluate against the same top-level subject", () => {
    const post = createSubject<Post>("Post", {
      authorName: (instance) => instance.author.name,
    })

    const policy = Policy.from({
      version: "1.0.0",
      rules: [[
        "allow", "Read", "Post",
        { $and: [{ authorName: "Alice" }, { $or: [{ status: "draft" }, { authorName: "Alice" }] }] },
      ]],
    })

    expect(policy.can(Read, post.wrap({ status: "published", author: { name: "Alice" } }))).toBe(true)
    expect(policy.can(Read, post.wrap({ status: "published", author: { name: "Bob" } }))).toBe(false)
  })

  test("`wrap` carries the field mapper forward unchanged", () => {
    const post = createSubject<Post>("Post", { authorName: (instance) => instance.author.name })
    const wrapped = post.wrap({ status: "draft", author: { name: "Alice" } })

    expect(wrapped.fieldMapper).toBe(post.fieldMapper)
  })
})

describe("SubjectFieldMapper: registered via KeycardConfig.mapper", () => {
  test("is consulted when the Subject in hand carries no fieldMapper of its own", () => {
    const post = createSubject<Post>("Post")
    const catalog = new SubjectFieldMapperCatalog({
      Post: { authorName: (instance: Post) => instance.author.name },
    })

    const policy = Policy.from(
      {
        version: "1.0.0",
        rules: [["allow", "Read", "Post", { authorName: "Alice" }]],
      },
      {},
      { mapper: catalog },
    )

    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Alice" } }))).toBe(true)
    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Bob" } }))).toBe(false)
  })

  test("a fieldMapper on the Subject itself takes precedence over the catalog", () => {
    const post = createSubject<Post>("Post", { authorName: (instance) => instance.author.name })
    const catalog = new SubjectFieldMapperCatalog({
      // Deliberately different, so the test can tell which one won.
      Post: { authorName: () => "Mismatched" },
    })

    const policy = Policy.from(
      {
        version: "1.0.0",
        rules: [["allow", "Read", "Post", { authorName: "Alice" }]],
      },
      {},
      { mapper: catalog },
    )

    expect(policy.can(Read, post.wrap({ status: "draft", author: { name: "Alice" } }))).toBe(true)
  })
})

describe("KeycardConfig: actions/subjects widen meta.actions/meta.subjects (EC-8)", () => {
  test("Policy validates rule actions/subjects against config.actions/config.subjects even without a matching meta declaration", () => {
    expect(() =>
      Policy.from(
        { version: "1.0.0", rules: [["allow", "Write", "Post"]] },
        {},
        { actions: [createAction("Read")], subjects: [createSubject("Post")] },
      ),
    ).toThrow()

    expect(() =>
      Policy.from(
        { version: "1.0.0", rules: [["allow", "Read", "Post"]] },
        {},
        { actions: [createAction("Read")], subjects: [createSubject("Post")] },
      ),
    ).not.toThrow()
  })
})
