import { Header } from "../sections/Header";
import { Hero } from "../sections/Hero";
import { Features } from "../sections/Features";
import { Footer } from "../sections/Footer";

/** Static public product shell; it never imports a desktop runtime or opens a control connection. */
export function App() {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <Header />
      <main id="main">
        <Hero />
        <Features />
      </main>
      <Footer />
    </>
  );
}
