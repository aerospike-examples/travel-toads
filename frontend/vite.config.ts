import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Dev-only: forwards /api to a locally-running backend (either
      // `mvn ... run` directly on 8081, or the docker-compose `backend`
      // service, which also publishes 8081 on the host). In production the
      // frontend's own nginx container does this same proxying — see
      // Dockerfile / nginx.conf.
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true,
        // Mirrors the production nginx config's `proxy_pass
        // http://backend:8081/;` (trailing slash), which strips the
        // `/api` prefix on the way to the backend. If the real backend
        // turns out to actually expect the `/api` prefix itself, delete
        // this rewrite (and the nginx.conf trailing slash) — see
        // frontend/README.md.
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
});
