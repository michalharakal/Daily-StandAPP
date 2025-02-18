plugins {
    id("org.antora") version "1.0.0"
}

antora {
    packages = mapOf(
        "asciidoctor-kroki" to "latest",
        "asciidoctor-plantuml" to "latest"
    )
}
