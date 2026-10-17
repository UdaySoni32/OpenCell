# OpenCell GitHub Pages website

The static project website is in `website/index.html` and is deployed by
`.github/workflows/pages.yml` whenever the `main` branch changes the website.

## Enable it once on GitHub

1. Open the repository's **Settings → Pages**.
2. Under **Build and deployment**, choose **GitHub Actions** as the source.
3. Push the `main` branch or run **Deploy GitHub Pages website** from the Actions tab.

The resulting address is normally:

```text
https://<owner>.github.io/<repository>/
```

The page is intentionally dependency-free: it can be previewed by opening
`website/index.html` locally and does not require Node, a package manager, or
secrets.
