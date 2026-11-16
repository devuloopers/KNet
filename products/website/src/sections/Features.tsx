import { Icon } from "../components/Icon";
import { ProductImage } from "../components/ProductImage";
import { DownloadLink } from "../components/DownloadLink";
import { defaultFeatureId, features, site } from "../content/site";

/** Build-time content remains navigable without JavaScript; a tiny local module enhances the tour. */
export function Features() {
  return (
    <>
      <section
        id="features"
        className="section container feature-tour"
        aria-labelledby="features-title"
        data-feature-tour
      >
        <h2 id="features-title" className="visually-hidden">
          Explore KNet features
        </h2>
        <nav
          className="feature-tabs"
          aria-label="Explore KNet features"
          data-feature-tabs
        >
          {features.map((feature) => (
            <a
              id={`tab-${feature.id}`}
              key={feature.id}
              href={`#feature-${feature.id}`}
              data-feature-tab={feature.id}
            >
              {feature.label}
            </a>
          ))}
        </nav>
        <div className="feature-panels">
          {features.map((feature) => (
            <article
              className="feature-panel showcase-layout"
              key={feature.id}
              id={`feature-${feature.id}`}
              data-feature-panel={feature.id}
              aria-labelledby={`heading-${feature.id}`}
            >
              <div className="feature-copy showcase-copy">
                <h3 id={`heading-${feature.id}`} className="showcase-title">
                  {feature.title}
                </h3>
                <p className="feature-description">{feature.description}</p>
                <p className="feature-detail">{feature.detail}</p>
                <a className="text-link" href={feature.docs}>
                  Explore {feature.label} <span aria-hidden="true">→</span>
                </a>
              </div>
              <figure className="feature-image" data-artwork={feature.id}>
                <ProductImage
                  id={feature.id}
                  deferred={feature.id !== defaultFeatureId}
                  alt={feature.alt}
                  sizes="(min-width: 1100px) 850px, 94vw"
                />
              </figure>
            </article>
          ))}
        </div>
        <p className="capability-note">
          HTTP/2, WebSocket, GraphQL WS, gRPC and live SSE are experimental.{" "}
          <a href={site.capabilityMatrix}>View capability details.</a>
        </p>
      </section>
      <section
        className="feature-pair container"
        aria-label="Network testing and connection handling"
      >
        {features.slice(3).map((feature) => (
          <article className="detail-card" key={feature.id}>
            <div className="detail-heading">
              <Icon
                name={
                  feature.id === "network-conditions" ? "conditions" : "shield"
                }
                width="30"
                height="30"
              />
              <div>
                <h2>
                  {feature.id === "network-conditions"
                    ? "Test real network conditions."
                    : "Keep protected traffic working."}
                </h2>
                <p>
                  {feature.id === "network-conditions"
                    ? "Control speed, latency and rules for each device."
                    : "Choose how TLS connections are handled."}
                </p>
              </div>
            </div>
            <a
              className="screenshot-link"
              href={feature.docs}
              aria-label={`Read about ${feature.label}`}
            >
              <ProductImage
                id={feature.id}
                alt={feature.alt}
                sizes="(min-width: 900px) 620px, 94vw"
              />
            </a>
          </article>
        ))}
      </section>
      <section
        className="section container devices-section"
        id="companion"
        aria-labelledby="devices-title"
      >
        <div className="device-workspace">
          <p className="section-label">Multi-device workspaces</p>
          <h2 id="devices-title">
            Your devices.
            <br />
            Their own workspace.
          </h2>
          <p>
            Keep each device’s traffic and controls separate. Save rules for one
            device or make them available globally.
          </p>
          <figure className="device-image">
            <ProductImage
              id="multi-device"
              alt="Real KNet device tabs showing an online Pixel 8 and an offline iPhone with retained sample traffic."
              sizes="(min-width: 1100px) 660px, 94vw"
            />
          </figure>
        </div>
        <div className="companion-workspace">
          <h3>Scan. Connect. Inspect.</h3>
          <p>Pair your Android device with a QR code.</p>
          <div className="phone-previews">
            <figure>
              <div className="phone-frame">
                <ProductImage
                  id="companion-pairing"
                  alt="Actual KNet Companion pairing screen with Scan desktop QR and Choose QR image controls."
                  sizes="(min-width: 900px) 180px, 40vw"
                />
              </div>
              <figcaption>Pair your desktop</figcaption>
            </figure>
            <figure>
              <div className="phone-frame">
                <ProductImage
                  id="companion-connected"
                  alt="Actual KNet Companion connected to the sample MacBook Pro desktop with inspection running."
                  sizes="(min-width: 900px) 180px, 40vw"
                />
              </div>
              <figcaption>Start inspecting</figcaption>
            </figure>
          </div>
          <a className="text-link" href={site.releases}>
            Get Android Companion on GitHub <span aria-hidden="true">→</span>
          </a>
          <p className="companion-note">
            Android Companion is experimental. See the{" "}
            <a href={`${site.repository}#knet-companion`}>platform notes</a>.
          </p>
        </div>
      </section>
      <section className="utilities container" aria-label="More ways to work">
        {[
          {
            icon: "download" as const,
            title: "Import & export",
            text: "Move captures between your tools.",
            href: `${site.repository}/blob/master/docs/capture_import.md`,
          },
          {
            icon: "copy" as const,
            title: "Copy what matters",
            text: "Share clean request and response details.",
            href: `${site.repository}/blob/master/docs/traffic_copy_details.md`,
          },
          {
            icon: "code" as const,
            title: "Kotlin scripting",
            text: "Automate checks in API Studio.",
            href: `${site.repository}#api-studio`,
          },
        ].map((item) => (
          <a href={item.href} className="utility" key={item.title}>
            <Icon name={item.icon} width="28" height="28" />
            <div>
              <h2>{item.title}</h2>
              <p>{item.text}</p>
            </div>
          </a>
        ))}
      </section>
      <section className="closing container" aria-labelledby="closing-title">
        <h2 id="closing-title">See what your app is sending.</h2>
        <div className="hero-actions">
          <DownloadLink />
          <a className="text-link" href={site.docs}>
            Documentation
          </a>
        </div>
        <p>Open source. Runs on your machine.</p>
      </section>
    </>
  );
}
