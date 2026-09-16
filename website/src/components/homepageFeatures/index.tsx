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
    title: "Define",
    description: (
      <>
        Build a policy from a fluent, type-safe <code>PolicyBuilder</code> API to create a <code>PolicyDefinition</code>
      </>
    ),
  },
  {
    number: "II",
    title: "Encode",
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
    description: (
      <>
        Read the <code>PolicyDefinition</code> into a <code>Policy</code> and quickly check permissions
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
