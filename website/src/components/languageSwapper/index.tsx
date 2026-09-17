import type { FC } from "react"
import { useEffect, useState } from "react"

import { SUPPORTED_LANGUAGES } from "@site/src/data/languages"

import styles from "./styles.module.css"

const SWAP_INTERVAL_MS = 2000

function pickRandomLanguage(exclude?: string): string {
  const candidates = SUPPORTED_LANGUAGES.filter((language) => language !== exclude)
  const pool = candidates.length > 0 ? candidates : SUPPORTED_LANGUAGES
  return pool[Math.floor(Math.random() * pool.length)]
}

export const LanguageSwapper: FC = () => {
  const [language, setLanguage] = useState(() => pickRandomLanguage())

  useEffect(() => {
    const intervalId = setInterval(() => {
      setLanguage((current) => pickRandomLanguage(current))
    }, SWAP_INTERVAL_MS)
    return () => clearInterval(intervalId)
  }, [])

  return (
    <span className={styles.wrapper}>
      <span key={language} className={styles.text}>
        {language}
      </span>
    </span>
  )
}
