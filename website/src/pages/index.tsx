import Link from "@docusaurus/Link"
import useDocusaurusContext from "@docusaurus/useDocusaurusContext"
import Heading from "@theme/Heading"
import Layout from "@theme/Layout"
import type { ReactNode } from "react"

import HomepageFeatures from "@site/src/components/homepageFeatures"
import { LanguageSwapper } from "@site/src/components/languageSwapper"
import { SOURCE_LANGUAGES, TARGET_LANGUAGES } from "@site/src/data/languages"

import styles from "./index.module.css"

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
          Write permissions in <LanguageSwapper languages={SOURCE_LANGUAGES} /> -
          Check them in <LanguageSwapper languages={TARGET_LANGUAGES} />
        </p>
        <p className={styles.preAlphaNotice}>
          Pre-alpha: the API and policy format may still change without notice.
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
      </main>
    </Layout>
  )
}
