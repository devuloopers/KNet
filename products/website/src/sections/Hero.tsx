import { DownloadLink } from "../components/DownloadLink";
import { preview } from "../../.generated/preview";

/** One sentence and the actual deterministic KNet render keep the homepage product-focused. */
export function Hero() {
  return (
    <section className="hero" aria-labelledby="hero-title">
      <div className="hero-copy container">
        <p className="eyebrow">Network Inspector &amp; API Studio</p>
        <h1 id="hero-title">
          Your app’s traffic.
          <br />
          <span>Clearly understood.</span>
        </h1>
        <p className="hero-description">
          Inspect traffic. Replay requests. Test real conditions.
        </p>
        <DownloadLink />
      </div>
      <figure className="product-preview container">
        <picture>
          <source
            type="image/avif"
            srcSet={preview.avif}
            sizes="(max-width: 1280px) 94vw, 1200px"
          />
          <img
            src={preview.default}
            srcSet={preview.webp}
            sizes="(max-width: 1280px) 94vw, 1200px"
            width="1600"
            height="960"
            fetchPriority="high"
            decoding="async"
            alt="KNet Desktop showing separate device tabs, captured demo requests and a formatted JSON response."
          />
        </picture>
      </figure>
    </section>
  );
}
