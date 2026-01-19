'use strict'

const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

/**
 * Antora extension to render Mermaid diagrams locally using mermaid-cli.
 * No external services - all rendering happens in the Docker container.
 */
module.exports.register = function ({ config }) {
  const logger = this.getLogger('mermaid-extension')
  const puppeteerConfig = path.join(__dirname, 'puppeteer-config.json')

  this.on('contentClassified', ({ contentCatalog }) => {
    const tempDir = '/tmp/mermaid-diagrams'

    // Create temp directory for diagrams
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true })
    }

    for (const file of contentCatalog.getFiles()) {
      if (!file.src || file.src.extname !== '.adoc') continue
      if (!file.contents) continue

      let content = file.contents.toString()
      let modified = false

      // Find all [mermaid] blocks
      const mermaidRegex = /\[mermaid\]\n----\n([\s\S]*?)----/g
      let match
      const replacements = []

      while ((match = mermaidRegex.exec(content)) !== null) {
        const diagramCode = match[1].trim()
        if (!diagramCode) continue

        const hash = crypto.createHash('md5').update(diagramCode).digest('hex').slice(0, 8)
        const inputFile = path.join(tempDir, `diagram-${hash}.mmd`)
        const outputFile = path.join(tempDir, `diagram-${hash}.svg`)

        try {
          // Write mermaid code to temp file
          fs.writeFileSync(inputFile, diagramCode)

          // Render with mermaid-cli (with --no-sandbox for Docker)
          execSync(`mmdc -i "${inputFile}" -o "${outputFile}" -b transparent -p "${puppeteerConfig}"`, {
            stdio: 'pipe',
            timeout: 60000
          })

          // Read generated SVG
          const svg = fs.readFileSync(outputFile, 'utf8')

          // Store replacement
          replacements.push({
            original: match[0],
            replacement: `++++\n${svg}\n++++`
          })

          logger.info(`Rendered diagram: ${hash}`)

        } catch (err) {
          logger.warn(`Failed to render diagram ${hash}: ${err.message}`)
          // Keep original mermaid block if rendering fails
        }
      }

      // Apply all replacements
      for (const { original, replacement } of replacements) {
        content = content.replace(original, replacement)
        modified = true
      }

      if (modified) {
        file.contents = Buffer.from(content)
      }
    }
  })
}
