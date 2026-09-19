import CodeBlock from "@theme/CodeBlock"
import Heading from "@theme/Heading"
import { clsx } from "clsx"
import type { ReactNode } from "react"

import styles from "./styles.module.css"

// language=txt
const EXAMPLE_JAVA_POLICY_BUILDER = (`
// UserPolicy.java
Action Create = new Action("Create");
Action Update = new Action("Update");
Action Delete = new Action("Delete");

Subject<Article> ArticleSubject = new Subject<>("Article");

Policy policy = new PolicyBuilder()
  .allow(Create, ArticleSubject)
  .allow(Update, ArticleSubject, Condition.field(Article::getOwnerId).eq(1))
  .deny(Delete, ArticleSubject, Condition.field(Article::getStatus).ne("archived"))
  .build();
  
if (policy.can(Create, ArticleSubject)) 
  createArticle();
  
if (policy.can(Update, ArticleSubject.wrap(article))) 
  updateArticle(article);

PolicyDefinition policyDef = policy.getDefinition();
`).trim()

const EXAMPLE_YAML_POLICY_DEF = (`
# policy.yaml
version: 1.0
rules:
  - [ allow, Create, Article ],
  - [ allow, Update, Article, { ownerId: 1 } ],
  - [ deny, Delete, Article, { status: { $ne: archived } } ]
`).trim()

const EXAMPLE_TS_POLICY_USAGE = (`
// policy.ts  
const res = await fetch("/api/user/me/policy.yaml")
const policyDef = await res.json() as PolicyDefinition
const policy = Policy.from(policyDef);

// check if user can create an article
if (policy.can(Actions.Create, Subjects.Article)) {
  // create article
}

// check if user can update an article
if (policy.can(Actions.Update, Subjects.Article.wrap(article))) {
  // update article
}

// throws if the user is not allowed to delete the article
policy.require(Actions.Delete, Subjects.Article.wrap(article)); 
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
        Build a policy from a fluent, type-safe <code>PolicyBuilder</code> API to create a <code>Policy</code> and/or
        a <code>PolicyDefinition</code>
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
