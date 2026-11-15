import { loadEnv } from "vite";
import { siteEnvironment } from "./site-environment.mjs";

siteEnvironment({
  ...loadEnv("production", process.cwd(), "KNET_"),
  ...process.env,
});
