import Heading from "@theme/Heading"
import { clsx } from "clsx"
import type { ReactNode } from "react"

import styles from "./styles.module.css"

type FeatureItem = {
  number: string
  title: string
  description: ReactNode
}

const FeatureList: FeatureItem[] = [
  {
    number: "I",
    title: "Define Once",
    description: (
      <>
        Build a policy from a fluent, type-safe <code>PolicyBuilder</code> API
        and get back a small, order-significant, JSON-encodable{" "}
        <code>PolicyDefinition</code> — one document, one source of truth.
      </>
    ),
  },
  {
    number: "II",
    title: "Enforce Anywhere",
    description: (
      <>
        A <code>PolicyDefinition</code> is plain JSON. Ship it to a browser,
        a mobile client, or a service in another language — every conformant
        implementation evaluates it identically.
      </>
    ),
  },
  {
    number: "III",
    title: "Type-Safe by Design",
    description: (
      <>
        Actions and Subjects are branded types. Typos, refactors, and
        invalid Action/Subject pairings are caught at compile time, not in
        production.
      </>
    ),
  },
]

function Feature({ number, title, description }: FeatureItem) {
  return (
    <div className={clsx("col col--4", styles.featureCol)}>
      <div className={styles.featureCard}>
        <div className={styles.featureNumber}>{number}</div>
        <Heading as="h3" className={styles.featureTitle}>
          {title}
        </Heading>
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
