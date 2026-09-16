import importAlias from "@dword-design/eslint-plugin-import-alias"
import { defineConfig } from "eslint/config"

import rootConfig from "../eslint.config.ts"

export default defineConfig([
  rootConfig,
  importAlias.configs.recommended,
  {
    rules: {
      "import-x/no-unresolved": "off",
      "check-file/filename-naming-convention": "off",
    },
  },
])
