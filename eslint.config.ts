import js from "@eslint/js"
import stylistic from "@stylistic/eslint-plugin"
import {defineConfig, globalIgnores} from "eslint/config"
import {createTypeScriptImportResolver} from "eslint-import-resolver-typescript"
import checkFile from "eslint-plugin-check-file"
import {importX} from "eslint-plugin-import-x"
import globals from "globals"
import tseslint from "typescript-eslint"

export default defineConfig([
  globalIgnores([
    "**/target/",
    "**/dist/",
    "**/.docusaurus",
  ]),
  tseslint.configs.recommended,
  js.configs.recommended,
  importX.flatConfigs.recommended,
  importX.flatConfigs.typescript,
  stylistic.configs.customize({
    indent: 2,
    quotes: "double",
    semi: false,
    jsx: true,
    arrowParens: true,
    braceStyle: "1tbs",
  }),
  {
    plugins: {
      "check-file": checkFile,
    },
    languageOptions: {
      // __APP_VERSION__ is a build-time constant injected by vite.config.ts's `define` — see
      // src/data/appVersion.ts and src/viteEnv.d.ts.
      globals: {...globals.browser, __APP_VERSION__: "readonly"},
      parserOptions: {
        projectService: true,
      },
    },
    settings: {
      "import-x/resolver-next": [createTypeScriptImportResolver()],
      "react": {
        // "detect" uses context.getFilename() which was removed in ESLint 10.
        // Update this when eslint-plugin-react adds ESLint 10 support.
        version: "19.0",
      },
    },
    rules: {
      ...{ // eslint-plugin-check-file rules
        "check-file/filename-naming-convention": [
          "error",
          {
            "**/*.{ts,tsx}": "CAMEL_CASE",
          },
          {
            ignoreMiddleExtensions: true,
          },
        ],
      },

      ...{ // builtin eslint rules
        "default-case": "error",
        "default-case-last": "error",
        "eqeqeq": ["error", "always"],
        "max-classes-per-file": ["error", 1],
        "max-depth": ["error", 4],
        "no-undef": "off",
        "no-eval": "error",
        "no-inner-declarations": "error",
        "no-restricted-syntax": [
          "error",
          {
            selector: "TSAsExpression > TSAsExpression[typeAnnotation.type=\"TSUnknownKeyword\"]",
            message:
              "Do not use \"as unknown as T\" (double type assertion) — it bypasses structural checks entirely. "
              + "See AGENTS.md § Type assertions for alternatives.",
          },
        ],
        "no-shadow": "error",
        "no-unused-vars": "off",
        "require-await": "error",
        "unicode-bom": ["error", "never"],
      },

      ...{ // @typescript-eslint rules
        "@typescript-eslint/consistent-type-exports": "error",
        "@typescript-eslint/consistent-type-imports": "error",
        "@typescript-eslint/no-empty-object-type": "off",
        "@typescript-eslint/no-unused-vars": ["error", {
          ignoreRestSiblings: true,
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
          destructuredArrayIgnorePattern: "^_",
          varsIgnorePattern: "^_",
        }],
        "@typescript-eslint/switch-exhaustiveness-check": ["error", {
          allowDefaultCaseForExhaustiveSwitch: true,
          considerDefaultExhaustiveForUnions: true,
          requireDefaultForNonUnion: true,
        }],

        // conflicts with TypeScript's function overloads
        "no-redeclare": "off",
        "default-case": "off",
      },

      ...{ // eslint-plugin-import-x rules
        "import-x/consistent-type-specifier-style": ["error", "prefer-top-level"],
        "import-x/default": "off",
        "import-x/extensions": ["error", "ignorePackages", {fix: true}],
        "import-x/no-cycle": "error",
        "import-x/no-named-as-default-member": "off",
        "import-x/first": "error",
        "import-x/newline-after-import": "error",
        "import-x/no-duplicates": "error",
        "import-x/order": [
          "error",
          {
            "pathGroups": [
              {
                pattern: "(#/**|#*/**)",
                group: "internal",
              },
            ],
            "groups": [
              "builtin",
              "external",
              "internal",
              ["parent", "sibling"],
              "index",
            ],
            "newlines-between": "always",
            "distinctGroup": true,
            "alphabetize": {
              order: "asc",
              orderImportKind: "asc",
            },
          },
        ],
      },

      ...{ // eslint-plugin-react rules
        "react/no-children-prop": "off",
        "react/no-unescaped-entities": "off",
        "react/react-in-jsx-scope": "off",
      },

      ...{ // @stylistic rules
        "@stylistic/jsx-one-expression-per-line": "off",
        "@stylistic/operator-linebreak": [
          "error", "before", {
            overrides: {
              "=": "after",
              "+=": "after",
              "-=": "after",
              "*=": "after",
            },
          }],
      },
    },
  },
  {
    files: [
      "src/routes/**",
      "src/data/migrations/**",
    ],
    rules: {
      "check-file/filename-naming-convention": "off",
    },
  },
  {
    // Namespaced selector catalogs (see docs/adr/0014-selector-input-decomposition.md) rely on
    // the TS `namespace` construct so members can reference each other by bare identifier without
    // clashing with the legacy top-level selectors kept in the same file. That's also why
    // `no-shadow` is off here: a namespace member deliberately reusing a legacy selector's name
    // (e.g. `ProfileSelectors.selectLifestyle` alongside the module-level `selectLifestyle`) is
    // the intended shape, not an accidental shadow.
    files: [
      "**/*.selectors.ts",
      "**/*.selector.ts",
    ],
    rules: {
      "@typescript-eslint/no-namespace": "off",
      "no-shadow": "off",
    },
  },
  {
    files: [
      "./env.node.ts",
      "./vite.config.ts",
      "./vitest.config.ts",
      "./eslint.config.ts",
    ],
    languageOptions: {
      globals: globals.node,
    },
  },
])
