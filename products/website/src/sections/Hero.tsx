import { DownloadLink } from "../components/DownloadLink";
import { ProductImage } from "../components/ProductImage";
import { site } from "../content/site";

/** The complete product capture fits the initial desktop viewport without being cropped. */
export function Hero() {
  return (
    <>
      <section
        className="hero showcase-layout container"
        aria-labelledby="hero-title"
      >
        <div className="hero-copy showcase-copy">
          <p className="eyebrow">Network Inspector &amp; API Studio</p>
          <h1 id="hero-title">
            Understand traffic.
            <br />
            <span>Control</span> every request.
          </h1>
          <p className="hero-description">
            Inspect, replay and test your app’s network traffic.
          </p>
          <div className="hero-actions">
            <DownloadLink />
            <a className="text-link" href={site.docs}>
              Read the docs
            </a>
          </div>
        </div>
        <figure className="product-preview">
          <ProductImage
            id="traffic"
            hero
            alt="KNet Desktop showing separate device tabs, captured demo requests and a formatted JSON response."
          />
        </figure>
      </section>
      <div className="protocols container">
        <ul aria-label="Protocol workspaces">
          {["HTTP / HTTPS", "GraphQL", "WebSocket", "GraphQL WS", "gRPC"].map(
            (label) => (
              <li key={label}>{label}</li>
            ),
          )}
        </ul>
        <a href={site.capabilityMatrix}>
          Protocol support <span aria-hidden="true">↗</span>
        </a>
      </div>
    </>
  );
}
