import { Brand } from "../components/Brand";
import { site } from "../content/site";

/** Short local-first statement and repository-backed support destinations. */
export function Footer() {
  return (
    <>
      <p className="project-note container">
        Open source. Runs on your machine.{" "}
        <a href={site.docs}>Explore the docs.</a>
      </p>
      <footer className="site-footer container">
        <Brand />
        <nav aria-label="Footer navigation">
          <a href={site.releases}>Releases</a>
          <a href={site.docs}>Docs</a>
          <a href={site.repository}>GitHub</a>
        </nav>
      </footer>
    </>
  );
}
