import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

/** The web product consumes only local brand assets and generated public artwork. */
export default defineConfig({
  plugins: [
    react(),
    {
      name: "knet-development-entry",
      apply: "serve",
      transformIndexHtml: () => [
        {
          tag: "script",
          attrs: { type: "module", src: "/src/development.tsx" },
          injectTo: "body",
        },
      ],
    },
  ],
  server: { port: 5174, strictPort: true, host: "127.0.0.1" },
  build: { target: "es2022", sourcemap: false },
});
