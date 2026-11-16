import { Brand } from "../components/Brand";
import { DownloadLink } from "../components/DownloadLink";
import { site } from "../content/site";

/** Essential navigation stays accessible on narrow viewports; downloads remain in the hero. */
export function Header() {
  return (
    <header className="site-header container">
      <Brand />
      <nav aria-label="Main navigation">
        <a className="section-nav" href="#features">
          Features
        </a>
        <a className="section-nav" href="#companion">
          Companion
        </a>
        <a href={site.docs}>Docs</a>
        <a href={site.repository}>GitHub</a>
        <div className="header-download">
          <DownloadLink compact />
        </div>
      </nav>
    </header>
  );
}
