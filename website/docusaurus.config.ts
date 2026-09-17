import type * as PluginContentDocs from "@docusaurus/plugin-content-docs"
import type * as Preset from "@docusaurus/preset-classic"
import type { Config } from "@docusaurus/types"
import { themes as prismThemes } from "prism-react-renderer"

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const GITHUB_URL = "https://github.com/CptnFizzbin/keycard"

const config: Config = {
  title: "KeyCard",
  tagline: "Define your access policy once. Enforce it everywhere.",
  favicon: "img/favicon.svg",

  url: "https://keycard.cptnfizzbin.dev",
  baseUrl: "/",

  organizationName: "CptnFizzbin",
  projectName: "keycard",

  onBrokenLinks: "throw",

  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  markdown: {
    hooks: {
      onBrokenMarkdownLinks: "warn",
    },
  },

  presets: [
    [
      "classic",
      {
        docs: {
          id: "default",
          path: "docs",
          routeBasePath: "docs",
          sidebarPath: "./sidebars.default.ts",
          editUrl: `${GITHUB_URL}/tree/main/website/`,
        },
        blog: false,
        theme: {
          customCss: "./src/css/custom.css",
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    [
      "@docusaurus/plugin-content-docs",
      {
        id: "js",
        path: "docs-js",
        routeBasePath: "js",
        sidebarPath: "./sidebars.js.ts",
        editUrl: `${GITHUB_URL}/tree/main/website/`,
      } satisfies PluginContentDocs.Options,
    ],
    [
      "@docusaurus/plugin-content-docs",
      {
        id: "java",
        path: "docs-java",
        routeBasePath: "java",
        sidebarPath: "./sidebars.java.ts",
        editUrl: `${GITHUB_URL}/tree/main/website/`,
      } satisfies PluginContentDocs.Options,
    ],
  ],

  themeConfig: {
    image: "img/logo.svg",
    colorMode: {
      defaultMode: "dark",
      respectPrefersColorScheme: true,
    },
    announcementBar: {
      id: "pre-alpha-notice",
      content:
        "⚠️ KeyCard is in <strong>pre-alpha</strong> — the API and policy format may change without notice. Not yet recommended for production use.",
      backgroundColor: "#a9781f",
      textColor: "#1b1b1b",
      isCloseable: true,
    },
    navbar: {
      title: "KeyCard",
      logo: {
        alt: "KeyCard Logo",
        src: "img/logo.svg",
      },
      items: [
        {
          type: "docSidebar",
          docsPluginId: "default",
          sidebarId: "guideSidebar",
          position: "left",
          label: "Guide",
        },
        {
          type: "custom-languageSelector",
          position: "left",
          label: "Language",
        },
        {
          href: GITHUB_URL,
          label: "GitHub",
          position: "right",
        },
      ],
    },
    footer: {
      style: "dark",
      links: [
        {
          title: "Guide",
          items: [
            { label: "Introduction", to: "/docs/intro" },
            { label: "Policy Definition", to: "/docs/policy-definition" },
            { label: "Condition Operators", to: "/docs/condition-operators" },
            { label: "Glossary", to: "/docs/glossary" },
          ],
        },
        {
          title: "Languages",
          items: [
            { label: "JavaScript", to: "/js/intro" },
            { label: "Java", to: "/java/intro" },
          ],
        },
        {
          title: "More",
          items: [
            { label: "GitHub", href: GITHUB_URL },
            { label: "Spec (source)", href: `${GITHUB_URL}/blob/main/SPEC.md` },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Cptn Fizzbin. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.duotoneDark,
      darkTheme: prismThemes.duotoneDark,
      additionalLanguages: ["java"],
    },
  } satisfies Preset.ThemeConfig,

  future: {
    v4: true,
  },
}

export default config
