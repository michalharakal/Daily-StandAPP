object ServerMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = createLocalAIServer(port = 8080)
        server.start(wait = true)
    }
}
