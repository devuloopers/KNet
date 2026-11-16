import { artwork } from "../../.generated/artwork";

export type ArtworkId = keyof typeof artwork;

/** A verified, aspect-preserving Compose capture with responsive local formats and reserved geometry. */
export function ProductImage({
  id,
  alt,
  hero = false,
  deferred = false,
  sizes = "(min-width: 1100px) 65vw, 94vw",
}: {
  id: ArtworkId;
  alt: string;
  hero?: boolean;
  deferred?: boolean;
  sizes?: string;
}) {
  const source = artwork[id];
  const picture = (
    <picture>
      <source type="image/avif" srcSet={source.avif} sizes={sizes} />
      <img
        src={source.default}
        srcSet={source.webp}
        sizes={sizes}
        width={source.width}
        height={source.height}
        alt={alt}
        loading={hero ? "eager" : "lazy"}
        fetchPriority={hero ? "high" : "auto"}
        decoding="async"
      />
    </picture>
  );
  if (!deferred) return picture;
  return (
    <div className="deferred-image" data-deferred-image>
      <picture>
        <source type="image/avif" data-srcset={source.avif} sizes={sizes} />
        <img
          data-src={source.default}
          data-srcset={source.webp}
          sizes={sizes}
          width={source.width}
          height={source.height}
          alt={alt}
          loading="lazy"
          decoding="async"
        />
      </picture>
      <noscript>{picture}</noscript>
    </div>
  );
}
