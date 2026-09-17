import type { FC } from "react"
import { useEffect, useState } from "react"

import styles from "./styles.module.css"

interface LanguageSwapperProps {
  languages: readonly string[]
}

const MIN_INTERVAL_MS = 3000
const MAX_INTERVAL_MS = 5000

function randomInterval(): number {
  return MIN_INTERVAL_MS + Math.random() * (MAX_INTERVAL_MS - MIN_INTERVAL_MS)
}

function pickRandomLanguage(languages: readonly string[], exclude?: string): string {
  const candidates = languages.filter((language) => language !== exclude)
  const pool = candidates.length > 0 ? candidates : languages
  return pool[Math.floor(Math.random() * pool.length)]
}

export const LanguageSwapper: FC<LanguageSwapperProps> = ({ languages }) => {
  const [language, setLanguage] = useState(() => pickRandomLanguage(languages))
  const maxLength = Math.max(...languages.map((lang) => lang.length))

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>

    const scheduleSwap = () => {
      timeoutId = setTimeout(() => {
        setLanguage((current) => pickRandomLanguage(languages, current))
        scheduleSwap()
      }, randomInterval())
    }

    scheduleSwap()
    return () => clearTimeout(timeoutId)
  }, [languages])

  return (
    <span className={styles.box} style={{ width: `${maxLength}ch` }}>
      <span key={language} className={styles.text}>
        {language}
      </span>
    </span>
  )
}
