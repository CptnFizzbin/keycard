import type { SidebarsConfig } from "@docusaurus/plugin-content-docs"

const sidebars: SidebarsConfig = {
  javaSidebar: [
    "intro",
    "api-reference",
    "examples",
    {
      type: "category",
      label: "Vision",
      items: ["vision-quickstart", "vision-real-backend"],
    },
  ],
}

export default sidebars
