import { Icon } from "../components/Icon";
import { features } from "../content/site";

/** A compact semantic list replaces verbose marketing sections. */
export function Features() {
  return (
    <section className="features container" aria-label="KNet highlights">
      <ul>
        {features.map((feature) => (
          <li key={feature.icon}>
            <Icon name={feature.icon} width="34" height="34" />
            <h2>{feature.label}</h2>
          </li>
        ))}
      </ul>
    </section>
  );
}
