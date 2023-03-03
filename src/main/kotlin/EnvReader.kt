object EnvReader {
    fun getEnv(name: String): String {
        val value: String? = System.getenv(name)
        requireNotNull(value) { "$name env variable must not be empty" }
        return value
    }
}