import Link from "@docusaurus/Link"
import { useLocation } from "@docusaurus/router"
import useBaseUrl from "@docusaurus/useBaseUrl"
import { clsx } from "clsx"
import React, { useCallback, useEffect, useRef, useState } from "react"

import styles from "./styles.module.css"

type LanguageOption = {
  key: string
  label: string
  badge: string
  href: string
  prefix: string
}

export default function LanguageSelectorNavbarItem(): React.ReactElement {
  const { pathname } = useLocation()
  const [isOpen, setIsOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const languages: LanguageOption[] = [
    {
      key: "js",
      label: "JavaScript / TypeScript",
      badge: "JS",
      href: useBaseUrl("/js/intro"),
      prefix: useBaseUrl("/js"),
    },
    {
      key: "java",
      label: "Java",
      badge: "JV",
      href: useBaseUrl("/java/intro"),
      prefix: useBaseUrl("/java"),
    },
  ]

  const active = languages.find((lang) => pathname.startsWith(lang.prefix))

  const close = useCallback(() => setIsOpen(false), [])

  useEffect(() => {
    if (!isOpen) {
      return undefined
    }
    const handleClick = (event: MouseEvent) => {
      if (
        containerRef.current
        && !containerRef.current.contains(event.target as Node)
      ) {
        close()
      }
    }
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        close()
      }
    }
    document.addEventListener("mousedown", handleClick)
    document.addEventListener("keydown", handleKey)
    return () => {
      document.removeEventListener("mousedown", handleClick)
      document.removeEventListener("keydown", handleKey)
    }
  }, [isOpen, close])

  return (
    <div ref={containerRef} className={styles.container}>
      <button
        type="button"
        className={clsx("navbar__link", styles.trigger)}
        aria-haspopup="true"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <span className={styles.triggerBadge}>{active ? active.badge : "⟡"}</span>
        <span>{active ? active.label.split(" ")[0] : "Language"}</span>
        <span className={styles.chevron} aria-hidden="true" />
      </button>
      <ul
        className={clsx(styles.menu, isOpen && styles.menuOpen)}
        role="menu"
      >
        {languages.map((lang) => (
          <li key={lang.key} role="none">
            <Link
              role="menuitem"
              to={lang.href}
              onClick={close}
              className={clsx(
                styles.menuItem,
                pathname.startsWith(lang.prefix) && styles.menuItemActive,
              )}
            >
              <span className={styles.menuBadge}>{lang.badge}</span>
              {lang.label}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
