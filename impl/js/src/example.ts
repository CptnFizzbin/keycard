import { createAction, createSubject, Policy, PolicyBuilder } from "./index.ts"
import { getLogger } from "./lib/logger.ts"

import type { InferActions, InferSubjects, KeycardConfig } from "./index"

const logger = getLogger()

// Define your action types
const Actions = {
  Create: createAction("Create"),
  Read: createAction("Read"),
  Update: createAction("Update"),
  Delete: createAction("Delete"),
} as const

type AppActions = InferActions<typeof Actions>

// Define your subject types
const Subjects = {
  Article: createSubject<{ id: number, owner_id: number, status: string }>("Article"),
  ListItem: createSubject<{ id: number, title: string, owner_id: number }>("ListItem"),
} as const

type AppSubjects = InferSubjects<typeof Subjects>

// Bundle the action/subject vocabulary into one KeycardConfig, shared by
// PolicyBuilder and Policy instead of kept in sync by hand
const config: KeycardConfig = {
  actions: Object.values(Actions),
  subjects: Object.values(Subjects),
}

// Build a policy using the type-safe definitions
const policyDef = new PolicyBuilder<AppActions, AppSubjects>({}, config)
  .allow(Actions.Create, Subjects.Article)
  .allow(Actions.Read, Subjects.Article)
  .allow(Actions.Update, Subjects.Article, { owner_id: 1 })
  .deny(Actions.Delete, Subjects.Article, { status: { $not: "archived" } })
  .buildDef()

// Create a policy instance
const policy = new Policy<AppActions, AppSubjects>(policyDef, {}, config)

// Type-safe permission checks
const article = Subjects.Article.wrap({ id: 1, owner_id: 1, status: "published" })

if (policy.can(Actions.Create, Subjects.Article)) {
  logger.info("✓ Can create articles")
}

if (policy.can(Actions.Update, article)) {
  logger.info("✓ Can update own article")
}

if (policy.can(Actions.Delete, article)) {
  logger.info("✓ Can delete article")
} else {
  logger.info("✗ Cannot delete non-archived article")
}

// Type safety: these would be caught at compile time
// policy.can(Actions.Create, "InvalidSubject"); // ❌ Type error
// policy.can("InvalidAction", Subjects.Article); // ❌ Type error
