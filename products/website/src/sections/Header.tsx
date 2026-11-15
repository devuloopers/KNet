import { Brand } from "../components/Brand";
import { DownloadLink } from "../components/DownloadLink";
import { site } from "../content/site";

/** Narrow screens retain Docs/GitHub; the prominent download action remains in the hero. */
export function Header() {
  return (
    <header className="site-header container">
      <Brand />
      <nav aria-label="Main navigation">
        <a href={site.docs}>Docs</a>
        <a href={site.repository}>GitHub</a>
        <div className="header-download">
          <DownloadLink compact />
        </div>
      </nav>
    </header>
  );
}
