object EnvReader {
    fun getEnv(name: String, default: String? = null): String {
        val value: String? = System.getenv(name) ?: default
        requireNotNull(value) { "$name env variable must not be empty" }
        return value
    }
}