import { Brand } from "../components/Brand";
import { site } from "../content/site";

/** Repository-backed footer destinations keep documentation and all releases directly accessible. */
export function Footer() {
  return (
    <footer className="site-footer container">
      <Brand />
      <nav aria-label="Footer navigation">
        <a href={site.docs}>Docs</a>
        <a href={site.repository}>GitHub</a>
        <a href={site.releases}>Releases</a>
      </nav>
    </footer>
  );
}
