import type { MetadataRoute } from "next";

// Development-only preview: DO NOT index until SEO/canonical migration is complete.
export default function robots(): MetadataRoute.Robots {
  return { rules: [{ userAgent: "*", disallow: "/" }] };
}
