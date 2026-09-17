import Link from "@docusaurus/Link"
import useDocusaurusContext from "@docusaurus/useDocusaurusContext"
import CodeBlock from "@theme/CodeBlock"
import Heading from "@theme/Heading"
import Layout from "@theme/Layout"
import type { ReactNode } from "react"

import HomepageFeatures from "@site/src/components/homepageFeatures"
import { LanguageSwapper } from "@site/src/components/languageSwapper"

import styles from "./index.module.css"

const POLICY_SAMPLE = `version: "1.0"
rules:
  - [allow, Create, Article]
  - [allow, Update, Article, { owner_id: 1 }]
  - [deny, Delete, Article, { status: { $not: "archived" } }]`

function HomepageHeader() {
  const { siteConfig } = useDocusaurusContext()
  return (
    <header className={styles.heroBanner}>
      <div className={styles.heroInner}>
        <span className={styles.eyebrow}>Cptn Fizzbin's</span>
        <Heading as="h1" className={styles.heroTitle}>
          {siteConfig.title}
        </Heading>
        <p className={styles.heroSubtitle}>
          Define in <LanguageSwapper /> -
          Apply in <LanguageSwapper />
        </p>
        <div className={styles.decoRule} />
        <div className={styles.buttons}>
          <Link className="button button--primary button--lg" to="/docs/intro">
            Read the Guide
          </Link>
          <Link className="button button--outline button--lg" to="/js/intro">
            JavaScript
          </Link>
          <Link className="button button--outline button--lg" to="/java/intro">
            Java
          </Link>
        </div>
      </div>
      <div className={styles.stepEdge} />
    </header>
  )
}

function PolicySample() {
  return (
    <section className={styles.codePanelSection}>
      <div className={styles.codePanel}>
        <div className={styles.codePanelHeader}>
          <span>Policy Definition</span>
          <span>v1.0</span>
        </div>
        <CodeBlock language="yaml">{POLICY_SAMPLE}</CodeBlock>
      </div>
    </section>
  )
}

export default function Home(): ReactNode {
  const { siteConfig } = useDocusaurusContext()
  return (
    <Layout
      title={siteConfig.title}
      description="KeyCard is a cross-language access-control library: define your authorization policy once, enforce it anywhere."
    >
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <PolicySample />
      </main>
    </Layout>
  )
}
