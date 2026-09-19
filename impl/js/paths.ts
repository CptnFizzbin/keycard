import * as path from "node:path"

export const JS_IMPL_DIR = path.resolve(import.meta.dirname)
export const ROOT_DIR = path.resolve(JS_IMPL_DIR, "../..")
export const KEYCARD_COMPLIANCE_FIXTURES = path.resolve(ROOT_DIR, "test/fixtures")
