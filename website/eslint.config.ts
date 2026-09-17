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
  {
    // Docusaurus's theme-override / swizzle mechanism matches these paths
    // against the upstream theme package's own files by exact, case-sensitive
    // name (e.g. `theme/NavbarItem/ComponentTypes`) — renaming them breaks
    // the override at runtime, so they're exempt from the camelCase rule.
    files: [
      "src/theme/**",
    ],
    rules: {
      "check-file/filename-naming-convention": "off",
    },
  },
])
