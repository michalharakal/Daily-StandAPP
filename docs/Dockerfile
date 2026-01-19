FROM node:20-alpine

# Install dependencies for Puppeteer/Chromium
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont \
    font-noto-emoji

# Set Puppeteer environment
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser \
    CHROME_BIN=/usr/bin/chromium-browser

# Install Antora and mermaid-cli
RUN npm install -g \
    @antora/cli@3.1 \
    @antora/site-generator@3.1 \
    @mermaid-js/mermaid-cli

WORKDIR /docs

# Copy extension files
COPY lib/ /docs/lib/

ENTRYPOINT ["antora"]
CMD ["antora-playbook.yml"]
