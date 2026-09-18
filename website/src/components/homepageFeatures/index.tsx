import CodeBlock from "@theme/CodeBlock"
import Heading from "@theme/Heading"
import { clsx } from "clsx"
import type { ReactNode } from "react"

import styles from "./styles.module.css"

const EXAMPLE_JAVA_POLICY_BUILDER = (`
Policy policy = new PolicyBuilder()
  .allow(create, article)
  .allow(update, article, Map.of("ownerId", 1))
  .deny(delete, article, Map.of("status", Map.of("$not", "archived")))
  .build();
`).trim()

const EXAMPLE_YAML_POLICY_DEF = (`
version: 1.0
rules:
  - [ allow, Create, Article ],
  - [ allow, Update, Article, { ownerId: 1 } ],
  - [ deny, Delete, Article, { status: { $not: archived } } ]
`).trim()

const EXAMPLE_TS_POLICY_USAGE = (`  
const policy = Policy.from(policyDefinition);

if (policy.can(Actions.Update, article)) {
  // update article
}

policy.require(Actions.Delete, article); // throws if denied
`).trim()

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
    code: EXAMPLE_JAVA_POLICY_BUILDER,
    description: (
      <>
        Build a policy from a fluent, type-safe <code>PolicyBuilder</code> API to create a <code>PolicyDefinition</code>
      </>
    ),
  },
  {
    number: "II",
    title: "Seralize",
    language: "yaml",
    code: EXAMPLE_YAML_POLICY_DEF,
    description: (
      <>
        A <code>PolicyDefinition</code> is a simple JSON encodeable object.
        Ship it to a browser, a mobile client, or a service in another language
      </>
    ),
  },
  {
    number: "III",
    title: "Enforce",
    language: "typescript",
    code: EXAMPLE_TS_POLICY_USAGE,
    description: (
      <>
        Read the <code>PolicyDefinition</code> into a <code>Policy</code> and quickly check permissions
      </>
    ),
  },
]

function Feature({ number, title, language, code, description }: FeatureItem) {
  return (
    <div className={styles.featureCard}>
      <div className={styles.featureLeft}>
        <div className={styles.featureHeader}>
          <div className={styles.featureNumber}>{number}</div>
          <Heading as="h3" className={styles.featureTitle}>
            {title}
          </Heading>
        </div>

        <div className={styles.divider} />

        <p className={styles.featureDescription}>{description}</p>
      </div>

      <div className={styles.divider} />

      <div className={styles.featureRight}>
        <div className={styles.featureCode}>
          <CodeBlock language={language}>{code}</CodeBlock>
        </div>
      </div>
    </div>
  )
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={clsx("container", styles.features)}>
      {FeatureList.map((props, idx) => (
        <Feature key={idx} {...props} />
      ))}
    </section>
  )
}
