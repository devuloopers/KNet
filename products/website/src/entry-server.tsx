import { renderToString } from "react-dom/server";
import { App } from "./app/App";

/** Build-only render: the release container serves HTML and never executes Node or React. */
export function render() {
  return renderToString(<App />);
}
