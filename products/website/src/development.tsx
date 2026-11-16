import { createRoot } from "react-dom/client";
import { useLayoutEffect } from "react";
import { App } from "./app/App";
import { enhanceWebsite } from "./showcase";

/** Development uses React; production only ships the independent, small tab-enhancement module. */
function DevelopmentApp() {
  useLayoutEffect(() => enhanceWebsite(), []);
  return <App />;
}
const root = document.getElementById("root");
if (!root) throw new Error("The website root is missing.");
createRoot(root).render(<DevelopmentApp />);
