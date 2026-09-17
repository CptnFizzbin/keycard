import type { FC } from "react"
import { useEffect, useRef, useState } from "react"

import styles from "./styles.module.css"

interface LanguageSwapperProps {
  languages: readonly string[]
}

const MIN_INTERVAL_MS = 3000
const MAX_INTERVAL_MS = 5000
const TRANSITION_DURATION_MS = 450

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
  const [previousLanguage, setPreviousLanguage] = useState<string | null>(null)
  const languageRef = useRef(language)
  languageRef.current = language
  const maxLength = Math.max(...languages.map((lang) => lang.length))

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout>

    const scheduleSwap = () => {
      timeoutId = setTimeout(() => {
        const current = languageRef.current
        setPreviousLanguage(current)
        setLanguage(pickRandomLanguage(languages, current))
        scheduleSwap()
      }, randomInterval())
    }

    scheduleSwap()
    return () => clearTimeout(timeoutId)
  }, [languages])

  useEffect(() => {
    if (previousLanguage === null) {
      return undefined
    }
    const clearId = setTimeout(() => setPreviousLanguage(null), TRANSITION_DURATION_MS)
    return () => clearTimeout(clearId)
  }, [previousLanguage])

  return (
    <span className={styles.box} style={{ width: `${maxLength}ch` }}>
      {previousLanguage !== null && (
        <span key={`exit-${previousLanguage}`} className={`${styles.text} ${styles.exit}`}>
          {previousLanguage}
        </span>
      )}
      <span key={`enter-${language}`} className={`${styles.text} ${styles.enter}`}>
        {language}
      </span>
    </span>
  )
}
