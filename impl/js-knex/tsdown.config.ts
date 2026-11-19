import { defineConfig } from "tsdown"

export default defineConfig({
  exports: {
    devExports: true,
  },
  outDir: "./dist",
  clean: true,
  sourcemap: true,
  unbundle: true,
  outputOptions: {
    format: "esm",
  },
})
