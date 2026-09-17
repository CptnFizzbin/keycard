import CodeBlock from "@theme/CodeBlock"
import Heading from "@theme/Heading"
import { clsx } from "clsx"
import type { ReactNode } from "react"

import styles from "./styles.module.css"

type FeatureItem = {
  number: string
  title: string
  language: string
  code: string
  description: ReactNode
}

const FeatureList: FeatureItem[] = [
  {
    number: "I",
    title: "Define",
    language: "java",
    code: `Policy policy = new PolicyBuilder()
    .allow(create, article)
    .allow(update, article, Map.of("ownerId", 1))
    .deny(delete, article, Map.of("status", Map.of("$not", "archived")))
    .build();`,
    description: (
      <>
        Build a policy from a fluent, type-safe <code>PolicyBuilder</code> API to create a <code>PolicyDefinition</code>
      </>
    ),
  },
  {
    number: "II",
    title: "Encode",
    language: "json",
    code: `{
  "version": "1.0",
  "rules": [
    ["allow", "Create", "Article"],
    ["allow", "Update", "Article", { "ownerId": 1 }],
    ["deny", "Delete", "Article", { "status": { "$not": "archived" } }]
  ]
}`,
    description: (
      <>
        A <code>PolicyDefinition</code> is a simple JSON encodeable object.
        Ship it to a browser, a mobile client, or a service in another language
      </>
    ),
  },
  {
    number: "III",
    title: "Apply",
    language: "typescript",
    code: `const policy = Policy.from(policyDefinition);

if (policy.can(Actions.Update, article)) {
  // update article
}

policy.require(Actions.Delete, article); // throws if denied`,
    description: (
      <>
        Read the <code>PolicyDefinition</code> into a <code>Policy</code> and quickly check permissions
      </>
    ),
  },
]

function Feature({ number, title, language, code, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4", styles.featureCol)}>
      <div className={styles.featureCard}>
        <div className={styles.featureNumber}>{number}</div>
        <Heading as="h3" className={styles.featureTitle}>
          {title}
        </Heading>
        <div className={styles.featureCode}>
          <CodeBlock language={language}>{code}</CodeBlock>
        </div>
        <p>{description}</p>
      </div>
    </div>
  )
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  )
}
