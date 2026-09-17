import type { FC } from "react"

interface LanguageSwapperProps {
  languages: string[]
}

export const LanguageSwapper: FC<LanguageSwapperProps> = ({
  languages,
}) => {
  return (
    <>{languages[0]}</>
  )
}
