import { createRoot } from "react-dom/client";
import { App } from "./app/App";

/** Serve-only entry: production uses build-rendered React markup without browser JavaScript. */
const root = document.getElementById("root");
if (!root) throw new Error("The website root is missing.");
createRoot(root).render(<App />);
